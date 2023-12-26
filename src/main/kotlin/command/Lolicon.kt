/*
 * Copyright (c) 2020-2023 Samarium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */
package io.github.samarium150.mirai.plugin.lolicon.command

import io.github.samarium150.mirai.plugin.lolicon.command.ImageUpdatedEntity
import io.github.samarium150.mirai.plugin.lolicon.command.ImageUrlEntity
import io.github.samarium150.mirai.plugin.lolicon.command.constant.*
import io.github.samarium150.mirai.plugin.lolicon.command.ImageCachedPool
import io.github.samarium150.mirai.plugin.lolicon.command.ImageSourceManager
import io.github.samarium150.mirai.plugin.lolicon.MiraiConsoleLolicon
import io.github.samarium150.mirai.plugin.lolicon.config.CommandConfig
import io.github.samarium150.mirai.plugin.lolicon.config.ExecutionConfig
import io.github.samarium150.mirai.plugin.lolicon.config.PluginConfig
import io.github.samarium150.mirai.plugin.lolicon.config.ReplyConfig
import io.github.samarium150.mirai.plugin.lolicon.data.PluginData
import io.github.samarium150.mirai.plugin.lolicon.data.RequestBody
import io.github.samarium150.mirai.plugin.lolicon.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.hasPermission
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.FlashImage
import net.mamoe.mirai.message.data.ForwardMessageBuilder
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.Image
import java.io.InputStream


object Lolicon : CompositeCommand(
    MiraiConsoleLolicon,
    primaryName = "lolicon",
    secondaryNames = CommandConfig.lolicon,
    description = "Lolicon发图命令"
) {

    val trusted: Permission by lazy {
        PermissionService.INSTANCE.register(
            MiraiConsoleLolicon.permissionId("trusted"),
            "受信任权限",
            MiraiConsoleLolicon.parentPermission
        )
    }

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @SubCommand("get", "来一张")
    @Description("根据标签发送涩图, 不提供则随机发送一张")
    suspend fun CommandSender.get(tags: String = "") {
        val mutex = getSubjectMutex(subject) ?: return
        if (mutex.isLocked) {
            logger.info("throttled")
            return
        }
        mutex.withLock {
            val (r18, recall, cooldown) = ExecutionConfig(subject)
            val body = if (tags.isNotEmpty())
                RequestBody(
                    r18, 1, listOf(), "", processTags(tags),
                    listOf(PluginConfig.size.name.lowercase()), PluginConfig.proxy
                )
            else RequestBody(
                r18, 1, listOf(), tags, listOf(),
                listOf(PluginConfig.size.name.lowercase()), PluginConfig.proxy
            )
            logger.info("request body: $body")
            val notificationReceipt = getNotificationReceipt()
            val response = processRequest(body) ?: return@withLock
            val imageData = response.data[0]
            if (!areTagsAllowed(imageData.tags)) {
                sendMessage(ReplyConfig.filteredTag)
                return@withLock
            }
            val url = imageData.urls[PluginConfig.size.name.lowercase()] ?: return@withLock
            val imgInfoReceipt =
                if (subject == null ||
                    PluginConfig.verbose && PluginConfig.messageType != PluginConfig.Type.Forward
                ) sendMessage(imageData.toReadable(url))
                else null
            if (subject == null && !PluginConfig.save)
                return@withLock
            val stream: InputStream?
            try {
                stream = getImageInputStream(url)
            } catch (e: Exception) {
                logger.error(e)
                sendMessage(ReplyConfig.networkError)
                return@withLock
            }
            if (subject == null) {
                runInterruptible(Dispatchers.IO) {
                    stream.close()
                }
                return@withLock
            }
            val image = (subject as Contact).uploadImage(stream)
            val imgReceipt = sendMessage(
                buildMessage(
                    subject as Contact,
                    if (PluginConfig.verbose) imageData.toReadable(url) else "",
                    image
                )
            )
            if (notificationReceipt != null)
                recall(RecallType.NOTIFICATION, notificationReceipt, 0)
            if (imgReceipt == null)
                return@withLock
            else if (recall > 0 && PluginConfig.recallImg)
                recall(RecallType.IMAGE, imgReceipt, recall)
            if (PluginConfig.verbose && imgInfoReceipt != null && recall > 0 && PluginConfig.recallImgInfo)
                recall(RecallType.IMAGE_INFO, imgInfoReceipt, recall)
            if (cooldown > 0)
                cooldown(subject, cooldown)
            runInterruptible(Dispatchers.IO) {
                stream.close()
            }
        }
    }

    @SubCommand("adv", "高级")
    @Description("根据JSON字符串发送涩图")
    suspend fun CommandSender.advanced(json: String) {
        val mutex = getSubjectMutex(subject) ?: return
        if (mutex.isLocked) {
            logger.info("throttled")
            return
        }
        mutex.withLock {
            val (r18, recall, cooldown) = ExecutionConfig(subject)
            val body: RequestBody = runCatching {
                Json.decodeFromString<RequestBody>(json)
            }.onFailure {
                sendMessage(ReplyConfig.invalidJson)
                logger.warning(it)
            }.getOrNull() ?: return@withLock
            logger.info(body.toString())
            val notificationReceipt = getNotificationReceipt()
            if (body.r18 != r18) {
                if (subject is Group && !(user as Member).isOperator()) {
                    sendMessage(ReplyConfig.nonAdminPermissionDenied)
                    return@withLock
                }
                if (subject is User && !this.hasPermission(trusted)) {
                    sendMessage(ReplyConfig.untrusted)
                    return@withLock
                }
            }
            val response = processRequest(body) ?: return@withLock
            if (subject != null && PluginConfig.messageType == PluginConfig.Type.Forward) {
                val contact = subject as Contact
                val imageMsgBuilder = ForwardMessageBuilder(contact)
                imageMsgBuilder.displayStrategy = CustomDisplayStrategy
                for (imageData in response.data) {
                    when {
                        imageData.urls.size > 1 -> {
                            imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                            for (url in imageData.urls.values) {
                                runCatching {
                                    val stream = getImageInputStream(url)
                                    val image = contact.uploadImage(stream)
                                    imageMsgBuilder.add(contact.bot, image)
                                    stream
                                }.onFailure {
                                    logger.error(it)
                                    imageMsgBuilder.add(contact.bot, PlainText(ReplyConfig.networkError))
                                }.onSuccess {
                                    runInterruptible(Dispatchers.IO) {
                                        it.close()
                                    }
                                }
                            }
                        }

                        imageData.urls.size == 1 -> {
                            runCatching {
                                val stream = getImageInputStream(imageData.urls.values.first())
                                val image = contact.uploadImage(stream)
                                imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                                imageMsgBuilder.add(contact.bot, image)
                                stream
                            }.onFailure {
                                logger.error(it)
                                imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                                imageMsgBuilder.add(contact.bot, PlainText(ReplyConfig.networkError))
                            }.onSuccess {
                                runInterruptible(Dispatchers.IO) {
                                    it.close()
                                }
                            }
                        }

                        else -> {
                            continue
                        }
                    }
                }
                val imgReceipt = sendMessage(imageMsgBuilder.build())
                if (notificationReceipt != null)
                    recall(RecallType.NOTIFICATION, notificationReceipt, 0)
                if (imgReceipt == null) {
                    return@withLock
                } else if (recall > 0 && PluginConfig.recallImg)
                    recall(RecallType.IMAGE, imgReceipt, recall)
                if (cooldown > 0)
                    cooldown(subject, cooldown)
            } else {
                val imageInfoMsgBuilder = MessageChainBuilder()
                val imageMsgBuilder = MessageChainBuilder()
                for (imageData in response.data) {
                    when {
                        imageData.urls.size > 1 -> {
                            for (url in imageData.urls.values) {
                                runCatching {
                                    val stream = getImageInputStream(url)
                                    val image = subject?.uploadImage(stream)
                                    if (image != null)
                                        if (PluginConfig.messageType == PluginConfig.Type.Flash)
                                            imageMsgBuilder.add(FlashImage(image))
                                        else
                                            imageMsgBuilder.add(image)
                                    stream
                                }.onFailure {
                                    logger.error(it)
                                    sendMessage(ReplyConfig.networkError)
                                }.onSuccess {
                                    imageInfoMsgBuilder.add(imageData.toReadable(imageData.urls))
                                    imageInfoMsgBuilder.add("\n")
                                    runInterruptible(Dispatchers.IO) {
                                        it.close()
                                    }
                                }
                            }
                        }

                        imageData.urls.size == 1 -> runCatching {
                            val stream = getImageInputStream(imageData.urls.values.first())
                            val image = subject?.uploadImage(stream)
                            if (image != null)
                                if (PluginConfig.messageType == PluginConfig.Type.Flash)
                                    imageMsgBuilder.add(FlashImage(image))
                                else
                                    imageMsgBuilder.add(image)
                            stream
                        }.onFailure {
                            logger.error(it)
                            sendMessage(ReplyConfig.networkError)
                        }.onSuccess {
                            imageInfoMsgBuilder.add(imageData.toReadable(imageData.urls))
                            imageInfoMsgBuilder.add("\n")
                            runInterruptible(Dispatchers.IO) {
                                it.close()
                            }
                        }

                        else -> {
                            continue
                        }
                    }
                }
                val imgInfoReceipt =
                    if (subject == null || PluginConfig.verbose)
                        sendMessage(imageInfoMsgBuilder.asMessageChain())
                    else null
                val imgReceipt = sendMessage(imageMsgBuilder.build())
                if (notificationReceipt != null)
                    recall(RecallType.NOTIFICATION, notificationReceipt, 0)
                if (imgReceipt == null) {
                    return@withLock
                } else if (recall > 0 && PluginConfig.recallImg)
                    recall(RecallType.IMAGE, imgReceipt, recall)
                if (PluginConfig.verbose && imgInfoReceipt != null && recall > 0 && PluginConfig.recallImgInfo)
                    recall(RecallType.IMAGE_INFO, imgInfoReceipt, recall)
                if (cooldown > 0)
                    cooldown(subject, cooldown)
            }
        }
    }


    @SubCommand("多来点", "来几张")
    @Description("多发几张图")
    suspend fun CommandSender.someimages(json: String = "") {
        val mutex = getSubjectMutex(subject) ?: return
        if (mutex.isLocked) {
            logger.info("throttled")
            return
        }
        mutex.withLock {
            val (r18, recall, cooldown) = ExecutionConfig(subject)
            var num = 2;
            if (json != null && !json.isEmpty()) {
                num = json.toInt()
            }
            if (num > 5 || num <=0) {
                num = 2
            }
            var str = "{\"num\":"+num+ "," +  "\"size\":" +"[\""  +PluginConfig.size.name.lowercase()+   "\"]"  +","+  "\"r18\":"  + r18  +","+ "\"proxy\":" + "\""+ PluginConfig.proxy+"\""  +"}"
            val body: RequestBody = runCatching {
                Json.decodeFromString<RequestBody>(str)
            }.onFailure {
                sendMessage(ReplyConfig.invalidJson)
                logger.warning(it)
            }.getOrNull() ?: return@withLock
            logger.info(body.toString())
            val notificationReceipt = getNotificationReceipt()
            if (body.r18 != r18) {
                if (subject is Group && !(user as Member).isOperator()) {
                    sendMessage(ReplyConfig.nonAdminPermissionDenied)
                    return@withLock
                }
                if (subject is User && !this.hasPermission(trusted)) {
                    sendMessage(ReplyConfig.untrusted)
                    return@withLock
                }
            }
            val response = processRequest(body) ?: return@withLock
            if (subject != null && PluginConfig.messageType == PluginConfig.Type.Forward) {
                val contact = subject as Contact
                val imageMsgBuilder = ForwardMessageBuilder(contact)
                imageMsgBuilder.displayStrategy = CustomDisplayStrategy
                for (imageData in response.data) {
                    when {
                        imageData.urls.size > 1 -> {
                            if(PluginConfig.verbose) {
                                imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                            }
                            for (url in imageData.urls.values) {
                                runCatching {
                                    val stream = getImageInputStream(url)
                                    val image = contact.uploadImage(stream)
                                    imageMsgBuilder.add(contact.bot, image)
                                    stream
                                }.onFailure {
                                    logger.error(it)
                                    imageMsgBuilder.add(contact.bot, PlainText(ReplyConfig.networkError))
                                }.onSuccess {
                                    runInterruptible(Dispatchers.IO) {
                                        it.close()
                                    }
                                }
                            }
                        }

                        imageData.urls.size == 1 -> {
                            runCatching {
                                val stream = getImageInputStream(imageData.urls.values.first())
                                val image = contact.uploadImage(stream)
                                if(PluginConfig.verbose) {
                                    imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                                }
                                imageMsgBuilder.add(contact.bot, image)
                                stream
                            }.onFailure {
                                logger.error(it)
                                if(PluginConfig.verbose) {
                                    imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                                }
                                imageMsgBuilder.add(contact.bot, PlainText(ReplyConfig.networkError))
                            }.onSuccess {
                                runInterruptible(Dispatchers.IO) {
                                    it.close()
                                }
                            }
                        }

                        else -> {
                            continue
                        }
                    }
                }
                val imgReceipt = sendMessage(imageMsgBuilder.build())
                if (notificationReceipt != null)
                    recall(RecallType.NOTIFICATION, notificationReceipt, 0)
                if (imgReceipt == null) {
                    return@withLock
                } else if (recall > 0 && PluginConfig.recallImg)
                    recall(RecallType.IMAGE, imgReceipt, recall)
                if (cooldown > 0)
                    cooldown(subject, cooldown)
            } else {
                val imageInfoMsgBuilder = MessageChainBuilder()
                val imageMsgBuilder = MessageChainBuilder()
                for (imageData in response.data) {
                    when {
                        imageData.urls.size > 1 -> {
                            for (url in imageData.urls.values) {
                                runCatching {
                                    val stream = getImageInputStream(url)
                                    val image = subject?.uploadImage(stream)
                                    if (image != null)
                                        if (PluginConfig.messageType == PluginConfig.Type.Flash)
                                            imageMsgBuilder.add(FlashImage(image))
                                        else
                                            imageMsgBuilder.add(image)
                                    stream
                                }.onFailure {
                                    logger.error(it)
                                    sendMessage(ReplyConfig.networkError)
                                }.onSuccess {
                                    imageInfoMsgBuilder.add(imageData.toReadable(imageData.urls))
                                    imageInfoMsgBuilder.add("\n")
                                    runInterruptible(Dispatchers.IO) {
                                        it.close()
                                    }
                                }
                            }
                        }

                        imageData.urls.size == 1 -> runCatching {
                            val stream = getImageInputStream(imageData.urls.values.first())
                            val image = subject?.uploadImage(stream)
                            if (image != null)
                                if (PluginConfig.messageType == PluginConfig.Type.Flash)
                                    imageMsgBuilder.add(FlashImage(image))
                                else
                                    imageMsgBuilder.add(image)
                            stream
                        }.onFailure {
                            logger.error(it)
                            sendMessage(ReplyConfig.networkError)
                        }.onSuccess {
                            imageInfoMsgBuilder.add(imageData.toReadable(imageData.urls))
                            imageInfoMsgBuilder.add("\n")
                            runInterruptible(Dispatchers.IO) {
                                it.close()
                            }
                        }

                        else -> {
                            continue
                        }
                    }
                }
                val imgInfoReceipt =
                    if (subject == null || PluginConfig.verbose)
                        sendMessage(imageInfoMsgBuilder.asMessageChain())
                    else null
                val imgReceipt = sendMessage(imageMsgBuilder.build())
                if (notificationReceipt != null)
                    recall(RecallType.NOTIFICATION, notificationReceipt, 0)
                if (imgReceipt == null) {
                    return@withLock
                } else if (recall > 0 && PluginConfig.recallImg)
                    recall(RecallType.IMAGE, imgReceipt, recall)
                if (PluginConfig.verbose && imgInfoReceipt != null && recall > 0 && PluginConfig.recallImgInfo)
                    recall(RecallType.IMAGE_INFO, imgInfoReceipt, recall)
                if (cooldown > 0)
                    cooldown(subject, cooldown)
            }
        }
    }

    @SubCommand("set", "设置")
    @Description("设置属性, 详见帮助信息")
    suspend fun CommandSenderOnMessage<MessageEvent>.set(property: PluginData.Property, value: Int) {
        if (fromEvent !is GroupMessageEvent && fromEvent !is FriendMessageEvent)
            return
        // if (fromEvent is GroupMessageEvent && !(fromEvent as GroupMessageEvent).sender.isOperator()) {
        //     sendMessage(ReplyConfig.nonAdminPermissionDenied)
        //     return
        // }
        if (!this.hasPermission(trusted)) {
            sendMessage(ReplyConfig.untrusted)
            return
        }
        logger.info("set $property to $value")
        if (value < 0) {
            sendMessage(value.toString() + ReplyConfig.illegalValue)
            return
        }
        if (setProperty(fromEvent.subject, property, value))
            sendMessage(ReplyConfig.setSucceeded)
        else
            sendMessage(value.toString() + ReplyConfig.illegalValue)
    }


    // @SubCommand("装填", "开始涩涩")
    // @Description("加载缓存池")
    // suspend fun CommandSenderOnMessage<MessageEvent>.reloadcache(reqNum: String = "") {
    //     if (fromEvent !is GroupMessageEvent && fromEvent !is FriendMessageEvent)
    //         return
    //     if (fromEvent is GroupMessageEvent && !(fromEvent as GroupMessageEvent).sender.isOperator()) {
    //         sendMessage(ReplyConfig.nonAdminPermissionDenied)
    //         return
    //     }
    //     if (fromEvent is FriendMessageEvent && !this.hasPermission(trusted)) {
    //         sendMessage(ReplyConfig.untrusted)
    //         return
    //     }
    //     logger.info("开始装填")
    //     val (r18, recall, cooldown) = ExecutionConfig(subject)
    //     var num = 2
    //     if (reqNum != null && !reqNum.isEmpty()) {
    //         num = reqNum.toInt()
    //     }
    //     if (num > 5 || num <=0) {
    //         num = 2
    //     }
    //     var str = "{\"num\":"+num+ "," +  "\"size\":" +"[\""  +PluginConfig.size.name.lowercase()+   "\"]"  +","+  "\"r18\":"  + r18  +","+ "\"proxy\":" + "\""+ PluginConfig.proxy+"\""  +"}"
    //     ImageCachedPool.instance.changeReq(str);
    //     ImageCachedPool.instance.isActiveNow = true;
    //     ImageCachedPool.instance.startRun();
        
    //     sendMessage("开始加载缓存池，配置为" + str)
    // }


    // @SubCommand("停止涩涩", "退膛")
    // @Description("停止缓存池")
    // suspend fun CommandSenderOnMessage<MessageEvent>.stopcache(reqNum: String = "") {
    //     if (fromEvent !is GroupMessageEvent && fromEvent !is FriendMessageEvent)
    //         return
    //     if (fromEvent is GroupMessageEvent && !(fromEvent as GroupMessageEvent).sender.isOperator()) {
    //         sendMessage(ReplyConfig.nonAdminPermissionDenied)
    //         return
    //     }
    //     if (fromEvent is FriendMessageEvent && !this.hasPermission(trusted)) {
    //         sendMessage(ReplyConfig.untrusted)
    //         return
    //     }
    //     logger.info("停止装填")
        
    //     ImageCachedPool.instance.isActiveNow = false;
    
        
    //     sendMessage("停止缓存池")
    // }

    // @SubCommand("搞快点", "gkd")
    // @Description("加载缓存")
    // suspend fun CommandSender.someimagescache(json: String = "") {
    //     val mutex = getSubjectMutex(subject) ?: return
    //     if (mutex.isLocked) {
    //         logger.info("throttled")
    //         return
    //     }
    //     mutex.withLock {
    //         val (r18, recall, cooldown) = ExecutionConfig(subject)
            
    //         val notificationReceipt = getNotificationReceipt()
            
    //         if (subject != null && PluginConfig.messageType == PluginConfig.Type.Forward) {
    //             val contact = subject as Contact
    //             val imageMsgBuilder = ForwardMessageBuilder(contact)
    //             imageMsgBuilder.displayStrategy = CustomDisplayStrategy
                
                    
    //             //imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))

    //             var needPending = false
    //             if (ImageCachedPool.instance.getSizePer() > 0.2) {
    //                 needPending = true
    //             }

    //             if (needPending) {
    //                 ImageCachedPool.instance.isActiveNow = false
    //             }

    //             for (i in 0 until 5) {
    //                 runCatching {
    //                     val stream = ImageCachedPool.instance.getImage()
    //                     val image = contact.uploadImage(stream)
    //                     imageMsgBuilder.add(contact.bot, image)
    //                     stream
    //                 }.onFailure {
    //                     logger.error(it)
    //                     imageMsgBuilder.add(contact.bot, PlainText(ReplyConfig.networkError))
    //                 }.onSuccess {
    //                     runInterruptible(Dispatchers.IO) {
    //                         it.close()
    //                     }
    //                 }
    //             }
                            
                
    //             val imgReceipt = sendMessage(imageMsgBuilder.build())
    //             if (notificationReceipt != null)
    //                 recall(RecallType.NOTIFICATION, notificationReceipt, 0)
    //             if (imgReceipt == null) {
    //                 return@withLock
    //             } else if (recall > 0 && PluginConfig.recallImg)
    //                 recall(RecallType.IMAGE, imgReceipt, recall)
    //             if (cooldown > 0)
    //                 cooldown(subject, cooldown)

    //             if (needPending) {
    //                 ImageCachedPool.instance.isActiveNow = true
    //             }
    //          }
                
    //     }
    // }

    @SubCommand("搞快点", "gkd")
    @Description("使用多个图库查询涩图")
    suspend fun CommandSenderOnMessage<MessageEvent>.someimagescache(json: String = "") {
        val mutex = getSubjectMutex(subject) ?: return
        if (mutex.isLocked) {
            logger.info("throttled")
            return
        }
        mutex.withLock {
            val (r18, recall, cooldown) = ExecutionConfig(subject)
            val req: MutableMap<String, Any?> = HashMap()
            req[ParamsConstant.R18] = r18

            val num: Int? = json.toIntOrNull()
            if (num != null) {
                if (num in 1..5) {
                    req[ParamsConstant.NUM] = num
                }
                else {
                    req[ParamsConstant.NUM] = 2
                }
            } else {
                req[ParamsConstant.NUM] = 2
                req[ParamsConstant.TAG] = json
            }
            req[ParamsConstant.SIZE] = PluginConfig.size.name.lowercase()
            val notificationReceipt = getNotificationReceipt()

            var isSp = false
            if (fromEvent is FriendMessageEvent) {
                isSp = true
            }
            
            if (subject != null && PluginConfig.messageType == PluginConfig.Type.Forward) {
                val contact = subject as Contact
                val imageMsgBuilder = ForwardMessageBuilder(contact)
                imageMsgBuilder.displayStrategy = CustomDisplayStrategy
                

                var cacheList: List<Any> = emptyList()
                
                if (isSp) {
                    cacheList = ImageCachedPool.getInstance().getImageByParamSp(req)
                    ?.let { it as? List<Any> }
                    ?.filterIsInstance<Any>()
                    ?: emptyList()
                } else {
                    cacheList = ImageCachedPool.getInstance().getImageByParamNormal(req)
                    ?.let { it as? List<Any> }
                    ?.filterIsInstance<Any>()
                    ?: emptyList()
                }
               

                if (cacheList.isNotEmpty()) {
                    
                    for (image in cacheList) {
                        val msg: ImageUpdatedEntity? = image as? ImageUpdatedEntity
                        if (msg != null) {

                            if (msg.getUrls() != null) {
                                imageMsgBuilder.add(contact.bot, PlainText(msg.getUrls().getDisplayString()+""))
                            }
                            
                            for (imageOne in msg.getImages()?.filterNotNull()?: emptyList()) {

                                val imageSelf: Image? =  imageOne as? Image
                                if (imageSelf!= null) {
                                    imageMsgBuilder.add(contact.bot, imageSelf)
                                }
                                else {
                                    imageMsgBuilder.add(contact.bot, PlainText("类型转换失败,非图片类型"))
                                }
                            }
                            
                        }
                        else {
                            imageMsgBuilder.add(contact.bot, PlainText("类型转换失败,非图片类型"))
                        }
                    }
                    
                }
                else {
                    logger.info("缓存穿透")
                    val allTimeStart = System.currentTimeMillis()
                
                    //imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                    val getUrlStart = System.currentTimeMillis()

                    var imageUrls: List<ImageUrlEntity> =  ImageSourceManager.getInstance()?.getImageUrlsEntity(req, isSp, false)
                        ?.filterNotNull()
                        ?: emptyList()
                    
                    

                    val getUrlTime = System.currentTimeMillis() - getUrlStart

                    if (imageUrls.isEmpty()) {
                        sendMessage(ReplyConfig.emptyImageData)
                        return@withLock
                    }


                    val uploadStart = System.currentTimeMillis()
                    var onlyUploadTime: Long = 0
                    var onlyDownloadTime: Long = 0
                    
                    for (entity in imageUrls) {
                        if (entity == null) {
                            continue
                        }
                        imageMsgBuilder.add(contact.bot, PlainText(entity.getDisplayString()))
                    
                        for (imageUrl in entity.getUrls()?.filterNotNull()?: emptyList()) {
                            runCatching {
                                logger.info(imageUrl)
                                val oneDownloadTimeStart = System.currentTimeMillis()
                                val stream = getImageInputStream(imageUrl)
                                onlyDownloadTime += (System.currentTimeMillis()-oneDownloadTimeStart)
                                val oneUploadTimeStart = System.currentTimeMillis()
                                val image = contact.uploadImage(stream)
                                onlyUploadTime += (System.currentTimeMillis()-oneUploadTimeStart)
                                imageMsgBuilder.add(contact.bot, image)
                                stream
                            }.onFailure {
                                logger.error(it)
                                imageMsgBuilder.add(contact.bot, PlainText(ReplyConfig.networkError))
                            }.onSuccess {
                                runInterruptible(Dispatchers.IO) {
                                    it.close()
                                }
                            }
                        }
                    }
                
                    

                    val uploadTime = System.currentTimeMillis() - uploadStart
                    val allTime = System.currentTimeMillis() - allTimeStart;
                    logger.info("总共耗时$allTime ms, 调用图片api接口耗时$getUrlTime ms, 下载上传图片总共耗时$uploadTime ms, 下载图片合计耗时$onlyDownloadTime ms, 上传图片合计耗时$onlyUploadTime ms")

                }
                                
                val imgReceipt = sendMessage(imageMsgBuilder.build())
                if (notificationReceipt != null)
                    recall(RecallType.NOTIFICATION, notificationReceipt, 0)
                if (imgReceipt == null) {
                    return@withLock
                } else if (recall > 0 && PluginConfig.recallImg)
                    recall(RecallType.IMAGE, imgReceipt, recall)
                if (cooldown > 0)
                    cooldown(subject, cooldown)
             }
            
        }
    }


    @SubCommand("setSource", "设置图库")
    @Description("设置图库, 详见帮助信息")
    suspend fun CommandSenderOnMessage<MessageEvent>.setSourceType(type: String = "") {
        if (fromEvent !is GroupMessageEvent && fromEvent !is FriendMessageEvent)
            return
        if (fromEvent is GroupMessageEvent && !(fromEvent as GroupMessageEvent).sender.isOperator()) {
            sendMessage(ReplyConfig.nonAdminPermissionDenied)
            return
        }
        if (fromEvent is FriendMessageEvent && !this.hasPermission(trusted)) {
            sendMessage(ReplyConfig.untrusted)
            return
        }

       
        var isSp = false
        if (fromEvent is FriendMessageEvent) {
            isSp = true
        }

        
        logger.info("set 图库 to $type")

        var isSucc = false

        if (isSp) {
            isSucc = ImageSourceManager.getInstance().setCurrentTypeSp(type)
        }
        else {
            isSucc = ImageSourceManager.getInstance().setCurrentTypeNormal(type)
        }
        
        if(isSucc) {
            if (isSp) {
                ImageCachedPool.getInstance().clearCacheSp()
            }
            else {
                ImageCachedPool.getInstance().clearCacheNormal()
            }
            ImageCachedPool.getInstance().startRun()
            sendMessage("成功设置图库为$type")
        }
        else {
            val allType = ImageSourceManager.getInstance().getAllType();
            sendMessage("不支持的图库类型:$type,当前支持以下类型:$allType")
        }
    }


    @SubCommand("上膛", "开始涩涩")
    @Description("加载缓存池")
    suspend fun CommandSenderOnMessage<MessageEvent>.reloadcache(reqNum: String = "") {
        if (fromEvent !is GroupMessageEvent && fromEvent !is FriendMessageEvent)
            return
        // if (fromEvent is GroupMessageEvent && !(fromEvent as GroupMessageEvent).sender.isOperator()) {
        //     sendMessage(ReplyConfig.nonAdminPermissionDenied)
        //     return
        // }
        if (!this.hasPermission(trusted)) {
            sendMessage(ReplyConfig.untrusted)
            return
        }
        logger.info("开始装填")
        val contact = subject as Contact
        
        ImageCachedPool.getInstance().boot(Runnable {
            runBlocking {
            val req: MutableMap<String, Any?> = HashMap()
            req[ParamsConstant.R18] = 0
            req[ParamsConstant.NUM] = 2
            req[ParamsConstant.TAG] = "";
            req[ParamsConstant.SIZE] = PluginConfig.size.name.lowercase()
            val imageUrls: List<ImageUrlEntity> = ImageSourceManager.getInstance()?.getImageUrlsEntity(req, false, true)
                    ?.filterNotNull()
                    ?: emptyList()


            val entities: MutableList<Any> = mutableListOf()

            

            for (entity in imageUrls) {

                val updatedEntity = ImageUpdatedEntity()

                val images: MutableList<Any> = mutableListOf()

                updatedEntity.setUrls(entity)

                updatedEntity.setImages(images)
                
                for (imageUrl in entity.getUrls()?.filterNotNull()?: emptyList()) {
                        runCatching {
                            logger.info(imageUrl)
                            val stream = getImageInputStream(imageUrl)
                            val image = contact.uploadImage(stream)
                            images.add(image)
                            stream
                        }.onFailure {
                            logger.error(it)
                        }.onSuccess {
                       
                            runInterruptible(Dispatchers.IO) {
                                it.close()
                            }
                        
                        }
                }
            }

            ImageCachedPool.getInstance().putImageNormal(entities);
            }
            
        }, Runnable { 
            runBlocking {
            val req: MutableMap<String, Any?> = HashMap()
            req[ParamsConstant.R18] = 1
            req[ParamsConstant.NUM] = 2
            req[ParamsConstant.TAG] = "";
            req[ParamsConstant.SIZE] = PluginConfig.size.name.lowercase()
            val imageUrls: List<ImageUrlEntity> = ImageSourceManager.getInstance()?.getImageUrlsEntity(req, true, true)
                    ?.filterNotNull()
                    ?: emptyList()


           val entities: MutableList<Any> = mutableListOf()

            

            for (entity in imageUrls) {

                val updatedEntity = ImageUpdatedEntity()

                val images: MutableList<Any> = mutableListOf()

                updatedEntity.setUrls(entity)

                updatedEntity.setImages(images)
                
                for (imageUrl in entity.getUrls()?.filterNotNull()?: emptyList()) {
                        runCatching {
                            logger.info(imageUrl)
                            val stream = getImageInputStream(imageUrl)
                            val image = contact.uploadImage(stream)
                            images.add(image)
                            stream
                        }.onFailure {
                            logger.error(it)
                        }.onSuccess {
                       
                            runInterruptible(Dispatchers.IO) {
                                it.close()
                            }
                        
                        }
                }
            }

            ImageCachedPool.getInstance().putImageSp(entities);
            }
        })
        
        
        sendMessage("开始加载缓存池")
    }


    @SubCommand("setAdd", "设置缓存")
    @Description("设置属性, 详见帮助信息")
    suspend fun CommandSenderOnMessage<MessageEvent>.set(key: String, value: String) {
        if (fromEvent !is GroupMessageEvent && fromEvent !is FriendMessageEvent)
            return
        if (!this.hasPermission(trusted)) {
            sendMessage(ReplyConfig.untrusted)
            return
        }
        logger.info("set $key to $value")
        

        var isSp = false
        if (fromEvent is FriendMessageEvent) {
            isSp = true
        }

        if (isSp) {
            ImageSourceManager.getInstance().putAdditionParamSp(key, value)
            sendMessage("成功设置特殊图库缓存属性$key 为 $value")
        }
        else {
            ImageSourceManager.getInstance().putAdditionParamNormal(key, value)
            sendMessage("成功设置普通图库缓存属性$key 为 $value")
        }
        
    }

    @SubCommand("clearAdd", "清除缓存设置")
    @Description("设置属性, 详见帮助信息")
    suspend fun CommandSenderOnMessage<MessageEvent>.set(reqNum: String = "") {
        if (fromEvent !is GroupMessageEvent && fromEvent !is FriendMessageEvent)
            return
        if (!this.hasPermission(trusted)) {
            sendMessage(ReplyConfig.untrusted)
            return
        }
        logger.info("clear cache config")

        var isSp = false
        if (fromEvent is FriendMessageEvent) {
            isSp = true
        }

        if (isSp) {
            ImageSourceManager.getInstance().clearAdditionParamSp()
            sendMessage("成功清理特殊图库缓存属性")
        }
        else {
            ImageSourceManager.getInstance().clearAdditionParamNormal()
            sendMessage("成功清理普通图库缓存属性")
        }
    }


    @SubCommand("排行", "rank")
    @Description("查询pixiv榜单")
    suspend fun CommandSenderOnMessage<MessageEvent>.rankQuery(json: String = "") {
        val mutex = getSubjectMutex(subject) ?: return
        if (mutex.isLocked) {
            logger.info("throttled")
            return
        }
        mutex.withLock {
            val (r18, recall, cooldown) = ExecutionConfig(subject)
            val req: MutableMap<String, Any?> = HashMap()
            req[ParamsConstant.R18] = r18

            val num: Int? = json.toIntOrNull()
            if (num != null) {
                if (num in 1..6) {
                    req[ParamsConstant.NUM] = num
                }
                else {
                    req[ParamsConstant.NUM] = 3
                }
            } else {
                req[ParamsConstant.NUM] = 3
                req[ParamsConstant.TAG] = json
            }
            req[ParamsConstant.SIZE] = PluginConfig.size.name.lowercase()
            val notificationReceipt = getNotificationReceipt()

            
            if (subject != null && PluginConfig.messageType == PluginConfig.Type.Forward) {
                val contact = subject as Contact
                val imageMsgBuilder = ForwardMessageBuilder(contact)
                imageMsgBuilder.displayStrategy = CustomDisplayStrategy
                
                
               

                val allTimeStart = System.currentTimeMillis()
                
                //imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                val getUrlStart = System.currentTimeMillis()

                val imageUrls: List<ImageUrlEntity> = ImageSourceManager.getInstance()?.getImageUrlsEntity(SourceTypeConstant.ACGMX_NEW,req)
                    ?.filterNotNull()
                    ?: emptyList()
                    
                    

                val getUrlTime = System.currentTimeMillis() - getUrlStart

                if (imageUrls.isEmpty()) {
                    sendMessage(ReplyConfig.emptyImageData)
                    return@withLock
                }


                val uploadStart = System.currentTimeMillis()
                var onlyUploadTime: Long = 0
                var onlyDownloadTime: Long = 0
                for (entity in imageUrls) {
                    if (entity == null) {
                        continue
                    }
                    imageMsgBuilder.add(contact.bot, PlainText(entity.getDisplayString()))
                    
                    for (imageUrl in entity.getUrls()?.filterNotNull()?: emptyList()) {
                        runCatching {
                            logger.info(imageUrl)
                            val oneDownloadTimeStart = System.currentTimeMillis()
                            val stream = getImageInputStream(imageUrl)
                            onlyDownloadTime += (System.currentTimeMillis()-oneDownloadTimeStart)
                            val oneUploadTimeStart = System.currentTimeMillis()
                            val image = contact.uploadImage(stream)
                            onlyUploadTime += (System.currentTimeMillis()-oneUploadTimeStart)
                            imageMsgBuilder.add(contact.bot, image)
                            stream
                        }.onFailure {
                            logger.error(it)
                            imageMsgBuilder.add(contact.bot, PlainText(ReplyConfig.networkError))
                        }.onSuccess {
                            runInterruptible(Dispatchers.IO) {
                                it.close()
                            }
                        }
                    }
                }

                val uploadTime = System.currentTimeMillis() - uploadStart
                val allTime = System.currentTimeMillis() - allTimeStart;
                logger.info("总共耗时$allTime ms, 调用图片api接口耗时$getUrlTime ms, 下载上传图片总共耗时$uploadTime ms, 下载图片合计耗时$onlyDownloadTime ms, 上传图片合计耗时$onlyUploadTime ms")

                
                                
                val imgReceipt = sendMessage(imageMsgBuilder.build())
                if (notificationReceipt != null)
                    recall(RecallType.NOTIFICATION, notificationReceipt, 0)
                if (imgReceipt == null) {
                    return@withLock
                } else if (recall > 0 && PluginConfig.recallImg)
                    recall(RecallType.IMAGE, imgReceipt, recall)
                if (cooldown > 0)
                    cooldown(subject, cooldown)
             }
            
        }
    }



    @SubCommand("排行2", "rank2")
    @Description("查询pixiv榜单")
    suspend fun CommandSenderOnMessage<MessageEvent>.rankQuery2(json: String = "") {
        val mutex = getSubjectMutex(subject) ?: return
        if (mutex.isLocked) {
            logger.info("throttled")
            return
        }
        mutex.withLock {
            val (r18, recall, cooldown) = ExecutionConfig(subject)
            val req: MutableMap<String, Any?> = HashMap()
            req[ParamsConstant.R18] = r18

            val num: Int? = json.toIntOrNull()
            if (num != null) {
                if (num in 1..6) {
                    req[ParamsConstant.NUM] = num
                }
                else {
                    req[ParamsConstant.NUM] = 3
                }
            } else {
                req[ParamsConstant.NUM] = 3
                req[ParamsConstant.TAG] = json
            }
            req[ParamsConstant.SIZE] = PluginConfig.size.name.lowercase()
            val notificationReceipt = getNotificationReceipt()

            
            if (subject != null && PluginConfig.messageType == PluginConfig.Type.Forward) {
                val contact = subject as Contact
                val imageMsgBuilder = ForwardMessageBuilder(contact)
                imageMsgBuilder.displayStrategy = CustomDisplayStrategy
                
                
               

                val allTimeStart = System.currentTimeMillis()
                
                //imageMsgBuilder.add(contact.bot, PlainText(imageData.toReadable(imageData.urls)))
                val getUrlStart = System.currentTimeMillis()

                val imageUrls: List<ImageUrlEntity> = ImageSourceManager.getInstance()?.getImageUrlsEntity(SourceTypeConstant.ACGMX,req)
                    ?.filterNotNull()
                    ?: emptyList()
                    
                    

                val getUrlTime = System.currentTimeMillis() - getUrlStart

                if (imageUrls.isEmpty()) {
                    sendMessage(ReplyConfig.emptyImageData)
                    return@withLock
                }


                val uploadStart = System.currentTimeMillis()
                var onlyUploadTime: Long = 0
                var onlyDownloadTime: Long = 0
                for (entity in imageUrls) {
                    if (entity == null) {
                        continue
                    }

                    imageMsgBuilder.add(contact.bot, PlainText(entity.getDisplayString()))
                    
                    for (imageUrl in entity.getUrls()?.filterNotNull()?: emptyList()) {
                        runCatching {
                            logger.info(imageUrl)
                            val oneDownloadTimeStart = System.currentTimeMillis()
                            val stream = getImageInputStream(imageUrl)
                            onlyDownloadTime += (System.currentTimeMillis()-oneDownloadTimeStart)
                            val oneUploadTimeStart = System.currentTimeMillis()
                            val image = contact.uploadImage(stream)
                            onlyUploadTime += (System.currentTimeMillis()-oneUploadTimeStart)
                            imageMsgBuilder.add(contact.bot, image)
                            stream
                        }.onFailure {
                            logger.error(it)
                            imageMsgBuilder.add(contact.bot, PlainText(ReplyConfig.networkError))
                        }.onSuccess {
                            runInterruptible(Dispatchers.IO) {
                                it.close()
                            }
                        }
                    }
                }

                val uploadTime = System.currentTimeMillis() - uploadStart
                val allTime = System.currentTimeMillis() - allTimeStart;
                logger.info("总共耗时$allTime ms, 调用图片api接口耗时$getUrlTime ms, 下载上传图片总共耗时$uploadTime ms, 下载图片合计耗时$onlyDownloadTime ms, 上传图片合计耗时$onlyUploadTime ms")

                
                                
                val imgReceipt = sendMessage(imageMsgBuilder.build())
                if (notificationReceipt != null)
                    recall(RecallType.NOTIFICATION, notificationReceipt, 0)
                if (imgReceipt == null) {
                    return@withLock
                } else if (recall > 0 && PluginConfig.recallImg)
                    recall(RecallType.IMAGE, imgReceipt, recall)
                if (cooldown > 0)
                    cooldown(subject, cooldown)
             }
            
        }
    }


    // @SubCommand("停止涩涩", "退膛")
    // @Description("停止缓存池")
    // suspend fun CommandSenderOnMessage<MessageEvent>.stopcache(reqNum: String = "") {
    //     if (fromEvent !is GroupMessageEvent && fromEvent !is FriendMessageEvent)
    //         return
    //     if (fromEvent is GroupMessageEvent && !(fromEvent as GroupMessageEvent).sender.isOperator()) {
    //         sendMessage(ReplyConfig.nonAdminPermissionDenied)
    //         return
    //     }
    //     if (fromEvent is FriendMessageEvent && !this.hasPermission(trusted)) {
    //         sendMessage(ReplyConfig.untrusted)
    //         return
    //     }
    //     logger.info("停止装填")
        
    //     ImageCachedPool.instance.isActiveNow = false;
    
        
    //     sendMessage("停止缓存池")
    // }
}

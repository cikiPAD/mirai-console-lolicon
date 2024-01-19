package io.github.samarium150.mirai.plugin.lolicon.command;



import io.github.samarium150.mirai.plugin.lolicon.command.constant.ParamsConstant;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;



public class ImageCachedPool extends Thread {

    public volatile boolean started = false;

    public volatile boolean isActiveNow = false;

    public volatile boolean isRunning = true;


    private static int size = ParamsConstant.CACHE_LIMIT;


    private static ConcurrentLinkedQueue<Object> images = new ConcurrentLinkedQueue<>();


    private static ConcurrentLinkedQueue<Object> imagesSp = new ConcurrentLinkedQueue<>();


    private static ImageCachedPool instance = new ImageCachedPool();

    private volatile int loopCount = 0;


    private Runnable runnable = null;

    private Runnable runnableSp = null;




    private ImageCachedPool() {
    }


    public static ImageCachedPool getInstance() {
        return instance;
    }

    /**
     * 只处理2张图片，不带keyword的请求
     * @return
     * @throws InterruptedException
     * @throws FileNotFoundException
     */
//    public Object getImageByParam(Map<String, Object> params) {
//        String tag = (String) params.get(ParamsConstant.TAG);
//        int num = (int) params.get(ParamsConstant.NUM);
//        int r18 = (int) params.get(ParamsConstant.R18);
//        //2张图片，且不带tag
//        if (num == 2 && (tag == null || tag.trim().length() == 0)) {
//            if (r18 == 1) {
//                Object ret =  imagesSp.poll();
//                startRun();
//                return ret;
//            }
//            else {
//                Object ret = images.poll();
//                startRun();
//                return ret;
//            }
//        }
//        else {
//            return null;
//        }
//    }



    public Object getImageByParamNormal(Map<String, Object> params) {
        String tag = (String) params.get(ParamsConstant.TAG);
        int num = (int) params.get(ParamsConstant.NUM);
        //2张图片，且不带tag
        if (num == 2 && (tag == null || tag.trim().length() == 0)) {
            Object ret = images.poll();
            startRun();
            return ret;
        }
        else {
            return null;
        }
    }


    public Object getImageByParamSp(Map<String, Object> params) {
        String tag = (String) params.get(ParamsConstant.TAG);
        int num = (int) params.get(ParamsConstant.NUM);
        //2张图片，且不带tag
        if (num == 2 && (tag == null || tag.trim().length() == 0)) {
            Object ret =  imagesSp.poll();
            startRun();
            return ret;
        }
        else {
            return null;
        }
    }

//    public void clearCache() {
//        this.images =  new ConcurrentLinkedQueue<>();
//        this.imagesSp = new ConcurrentLinkedQueue<>();
//    }

    public void clearCacheNormal() {
        this.images = new ConcurrentLinkedQueue<>();
    }

    public void clearCacheSp() {
        this.imagesSp = new ConcurrentLinkedQueue<>();
    }

//    public void putImage(Object entities,Map<String, Object> params) {
//        int r18 = (int) params.get(ParamsConstant.R18);
//        if (r18 == 1) {
//            imagesSp.offer(entities);
//            if (imagesSp.size()>size*2) {
//                System.out.println("imagesSp超载,数量"+ imagesSp.size());
//            }
//        }
//        else {
//            images.offer(entities);
//            if (images.size()>size*2) {
//                System.out.println("images超载,数量"+ images.size());
//            }
//        }
//    }


    public void putImageNormal(Object entities) {
        images.offer(entities);
        if (images.size()>size*2) {
            System.out.println("images超载,数量"+ images.size());
        }
        
    }

    public void putImageSp(Object entities) {
        imagesSp.offer(entities);
        if (imagesSp.size()>size*2) {
            System.out.println("imagesSp超载,数量"+ imagesSp.size());
        }
    }


    public void startRun() {
        loopCount = 0;
        isActiveNow = true;
    }

    public void stopRun() {
        isActiveNow = false;
    }

    public void boot(Runnable runnable, Runnable runnableSp) {
        if (started) {
            return;
        }
        else {
            this.runnable = runnable;
            this.runnableSp = runnableSp;
            started = true;
            isActiveNow = true;
            this.start();
        }
    }

    public void shutdown() {
        isRunning = false;
    }



    @Override
    public void run() {

        while(isRunning) {

            try {
                
                while (isActiveNow) {

                    loopCount++;
                    //避免频繁上传，连续循环5次就关闭
                    System.out.println("填充循环计数count:" + loopCount);
                    if (loopCount >= size) {
                        isActiveNow = false;
                    }
                    
                    Thread.sleep(1000);
                    if (images.size() < size) {
                        long startTime = System.currentTimeMillis();
                        System.out.println(String.format("开始填充普通图库,当前缓存大小: %s", images.size() + ""));
                        runnable.run();
                        Thread.sleep(500);
                        System.out.println(String.format("填充普通图库执行结束,耗时%s ms,当前缓存大小: %s", (System.currentTimeMillis() - startTime)+ "", images.size()+"" ));
                    }

                    if (imagesSp.size() < size) {
                        long startTime = System.currentTimeMillis();
                        System.out.println(String.format("开始填充特殊图库,当前缓存大小: %s", imagesSp.size() + ""));
                        runnableSp.run();
                        Thread.sleep(500);
                        System.out.println(String.format("填充特殊图库执行结束,耗时%s ms,当前缓存大小: %s", (System.currentTimeMillis() - startTime)+ "", imagesSp.size() + "" ));
                    }
                    
                    
                    if (isActiveNow == false) {
                        System.out.println("停止填充");
                    }
                    
                }
            }
            catch(Exception e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}

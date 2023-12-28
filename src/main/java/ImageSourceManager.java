package io.github.samarium150.mirai.plugin.lolicon.command;

import io.github.samarium150.mirai.plugin.lolicon.command.constant.ParamsConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.SourceTypeConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.impl.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多个文件来源的统一入口
 * 不引入spring，在kotlin插件环境下使用
 */
public class ImageSourceManager {

    public static ImageSourceManager instance = new ImageSourceManager();

    private String currentTypeNormal = SourceTypeConstant.NYAN;

    private String currentTypeSp = SourceTypeConstant.NYAN;

    private Map<String, Object> additionParamNormal = new ConcurrentHashMap<>();

    private Map<String, Object> additionParamSp = new ConcurrentHashMap<>();


    private ImageSourceManager() {
    }

    public static ImageSourceManager getInstance() {
        return instance;
    }

    private Map<String, ImageSourceInterface> sources = new HashMap<>();

    public void register(ImageSourceInterface source) {
        sources.put(source.getType(), source);
    }

    public void init() {
        register(new LoliconSourceImpl());
        register(new NyanSourceImpl());
        register(new JitsuSourceImpl());
        register(new YdSourceImpl());
        register(new XljSourceImpl());
        register(new VvhanSourceImpl());
        register(new TangdouzSourceImpl());
        register(new StarChenTSourceImpl());
        register(new AcgMxSourceImpl());
        register(new AcgMxSourceImplNew());
        register(new PidUidSearchImpl());
        register(new PicSearchPicImpl());
    }


    public List<InputStream> getImageStream(String type, Map<String, Object> params) {
        if (sources.containsKey(type)) {
            return sources.get(type).getImageStream(params);
        }
        else {
            throw new IllegalArgumentException("不支持类型");
        }
    }


    public List<String> getImageUrls(String type, Map<String, Object> params) {
        try {
            if (sources.containsKey(type)) {
                return sources.get(type).getImageUrl(filterNullValues(params));
            } else {
                return new ArrayList<>();
            }
        }
        catch (Exception e){
            return new ArrayList<>();
        }
    }


    public List<ImageUrlEntity> getImageUrlsEntity(String type, Map<String, Object> params) {
        try {
            if (sources.containsKey(type)) {
                return sources.get(type).getImageUrlEntity(filterNullValues(params));
            } else {
                return new ArrayList<>();
            }
        }
        catch (Exception e){
            e.printStackTrace();
            return new ArrayList<>();
        }
    }



    public List<ImageUrlEntity> getImageUrlsEntity(Map<String, Object> params, boolean isSp, boolean needAddParm) {
        try {

            if (isSp) {

                if (needAddParm) {
                    if (!additionParamSp.isEmpty()) {
                        for (String paramKey : additionParamSp.keySet()) {
                            params.put(paramKey, additionParamSp.get(paramKey));
                        }
                    }
                }


                if (sources.containsKey(currentTypeSp)) {
                    return sources.get(currentTypeSp).getImageUrlEntity(filterNullValues(params));
                } else {
                    return new ArrayList<>();
                }


            }
            else {

                if (needAddParm) {
                    if (!additionParamNormal.isEmpty()) {
                        for (String paramKey : additionParamNormal.keySet()) {
                            params.put(paramKey, additionParamNormal.get(paramKey));
                        }
                    }
                }


                if (sources.containsKey(currentTypeNormal)) {
                    return sources.get(currentTypeNormal).getImageUrlEntity(filterNullValues(params));
                } else {
                    return new ArrayList<>();
                }

            }

        }
        catch (Exception e){
            return new ArrayList<>();
        }
    }




    public List<String> getImageUrlsNormal(Map<String, Object> params, boolean needAddParm) {
        try {
            if (needAddParm) {
                if (!additionParamNormal.isEmpty()) {
                    for (String paramKey : additionParamNormal.keySet()) {
                        params.put(paramKey, additionParamNormal.get(paramKey));
                    }
                }
            }


            if (sources.containsKey(currentTypeNormal)) {
                return sources.get(currentTypeNormal).getImageUrl(filterNullValues(params));
            } else {
                return new ArrayList<>();
            }
        }
        catch (Exception e){
            return new ArrayList<>();
        }
    }


    public List<String> getImageUrlsSp(Map<String, Object> params, boolean needAddParm) {
        try {

            if (needAddParm) {
                if (!additionParamSp.isEmpty()) {
                    for (String paramKey : additionParamSp.keySet()) {
                        params.put(paramKey, additionParamSp.get(paramKey));
                    }
                }
            }


            if (sources.containsKey(currentTypeSp)) {
                return sources.get(currentTypeSp).getImageUrl(filterNullValues(params));
            } else {
                return new ArrayList<>();
            }
        }
        catch (Exception e){
            return new ArrayList<>();
        }
    }

    public void clearAdditionParamNormal() {
        additionParamNormal = new ConcurrentHashMap<>();
    }

    public void clearAdditionParamSp() {
        additionParamSp = new ConcurrentHashMap<>();
    }

    public void putAdditionParamNormal(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        Object legalValue = castSomeValue(key, value);
        additionParamNormal.put(key, legalValue);
    }

    public void putAdditionParamSp(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        Object legalValue = castSomeValue(key, value);
        additionParamSp.put(key, legalValue);
    }


    public static Map<String, Object> filterNullValues(Map<String, Object> params) {
        Map<String, Object> filteredParams = new HashMap<>();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value != null) {
                filteredParams.put(key, value);
            }
        }

        return filteredParams;
    }



    public boolean setCurrentTypeNormal(String type) {


        if (sources.containsKey(type)&& sources.get(type).visible()) {
            currentTypeNormal = type;
            return true;
        }
        else {
            return false;
        }
    }

    public boolean setCurrentTypeSp(String type) {


        if (sources.containsKey(type) && sources.get(type).visible()) {
            currentTypeSp = type;
            return true;
        }
        else {
            return false;
        }
    }

    public String getAllType() {
        return String.join(",",sources.keySet().stream().filter((p)->sources.get(p).visible()).collect(Collectors.toSet()));
    }


    public Object castSomeValue(String key, Object value) {
        if (ParamsConstant.NUM.equalsIgnoreCase(key)) {
            int num = Integer.valueOf((String) value);
            if (num<=0 || num > 10) {
                System.out.println("num数量非法，设置为2");
                num = 2;
            }
            return num;
        }

        if (ParamsConstant.R18.equalsIgnoreCase(key)) {
            int r18 = Integer.valueOf((String) value);
            return r18;
        }
        return value;
    }


}

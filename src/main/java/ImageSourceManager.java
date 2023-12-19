package io.github.samarium150.mirai.plugin.lolicon.command;

import io.github.samarium150.mirai.plugin.lolicon.command.constant.ParamsConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.SourceTypeConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.impl.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多个文件来源的统一入口
 * 不引入spring，在kotlin插件环境下使用
 */
public class ImageSourceManager {

    public static ImageSourceManager instance = new ImageSourceManager();

    private String currentTypeNormal = SourceTypeConstant.NYAN;

    private String currentTypeSp = SourceTypeConstant.NYAN;



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


    

    public List<String> getImageUrlsNormal(Map<String, Object> params) {
        try {
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


    public List<String> getImageUrlsSp(Map<String, Object> params) {
        try {
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
        if (sources.containsKey(type)) {
            currentTypeNormal = type;
            return true;
        }
        else {
            return false;
        }
    }

    public boolean setCurrentTypeSp(String type) {
        if (sources.containsKey(type)) {
            currentTypeSp = type;
            return true;
        }
        else {
            return false;
        }
    }

    public String getAllType() {
        return String.join(",",sources.keySet());
    }


}

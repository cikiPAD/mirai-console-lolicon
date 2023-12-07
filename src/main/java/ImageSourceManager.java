package io.github.samarium150.mirai.plugin.lolicon.command;

import io.github.samarium150.mirai.plugin.lolicon.command.constant.ParamsConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.SourceTypeConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.impl.JitsuSourceImpl;
import io.github.samarium150.mirai.plugin.lolicon.command.impl.LoliconSourceImpl;
import io.github.samarium150.mirai.plugin.lolicon.command.impl.NyanSourceImpl;
import io.github.samarium150.mirai.plugin.lolicon.command.impl.YdSourceImpl;

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

    private String currentType = SourceTypeConstant.NYAN;


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


    public List<String> getImageUrls(Map<String, Object> params) {
        try {
            if (sources.containsKey(currentType)) {
                return sources.get(currentType).getImageUrl(filterNullValues(params));
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


    public String getCurrentType() {
        return currentType;
    }

    public boolean setCurrentType(String type) {
        if (sources.containsKey(type)) {
            currentType = type;
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

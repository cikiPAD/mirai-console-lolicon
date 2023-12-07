package io.github.samarium150.mirai.plugin.lolicon.command.impl;

import com.google.gson.Gson;
import io.github.samarium150.mirai.plugin.lolicon.command.ImageSourceInterface;
import io.github.samarium150.mirai.plugin.lolicon.command.LoliHttpClient;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.ParamsConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.SourceTypeConstant;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoliconSourceImpl implements ImageSourceInterface {


    public String url = "https://api.lolicon.app/setu/v2";

    @Override
    public String getType() {
        return SourceTypeConstant.LOLICON;
    }

    @Override
    public List<InputStream> getImageStream(Map<String, Object> params) {
        throw new UnsupportedOperationException("不支持此操作");
    }


    @Override
    public List<String> getImageUrl(Map<String, Object> params) {
        Map<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/json");
        Gson gson = new Gson();
        String reqJson = gson.toJson(standardParams(params));
        String s = LoliHttpClient.postForBody(url, reqJson, header);
        if (s == null) {
            return new ArrayList<>();
        }

        Map map = gson.fromJson(s, Map.class);
        List<Map<String, Map<String, Object>>> data = (List<Map<String, Map<String, Object>>>) map.get("data");

        List<String> ret = new ArrayList<>();
        for (Map<String, Map<String, Object>> one:data) {
            Map<String, Object> urlsData = one.get("urls");
            for (String key :urlsData.keySet()) {
                ret.add((String) urlsData.get(key));
            }
        }
        return ret;
    }

    @Override
    public Map<String, Object> standardParams(Map<String, Object> params) {
        if (params.containsKey(ParamsConstant.TAG)) {
            List<String> tagList = handleTags((String) params.get(ParamsConstant.TAG));
            if (tagList == null || tagList.isEmpty()) {
                params.remove(tagList);
            }
            else {
                params.put(ParamsConstant.TAG, tagList);
            }
        }


        return params;
    }


    public List<String> handleTags(String tags) {
        if (tags == null || tags.trim().length() == 0) {
            return new ArrayList<>();
        }
        String[] arr = tags.split("\\&");

        List<String> ret = new ArrayList<>();
        for (String tmp:arr) {
            ret.add(tmp);
        }
        return ret;
    }


}

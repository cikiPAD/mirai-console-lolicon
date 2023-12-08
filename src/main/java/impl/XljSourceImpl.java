package io.github.samarium150.mirai.plugin.lolicon.command.impl;

import com.google.gson.Gson;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.SourceTypeConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XljSourceImpl extends SimpleSourceImpl {
    @Override
    String getUrl() {
        return "https://www.onexiaolaji.cn/RandomPicture/api/";
    }

    @Override
    List<String> getImageUrlsFromResp(String resp) {

        if (resp == null) {
            return new ArrayList<>();
        }
        Gson gson = new Gson();
        Map map = gson.fromJson(resp, Map.class);
        String img = (String) map.get("url");

        List<String> ret = new ArrayList<>();

        ret.add(img);
        return ret;
    }

    @Override
    public String getType() {
        return SourceTypeConstant.XLJ;
    }

    @Override
    public Map<String, Object> standardParams(Map<String, Object> params) {
        params.put("type", "json");
        params.put("key", "qq249663924");
        return params;
    }
}

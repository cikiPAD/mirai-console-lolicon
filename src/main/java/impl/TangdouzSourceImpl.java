package io.github.samarium150.mirai.plugin.lolicon.command.impl;

import io.github.samarium150.mirai.plugin.lolicon.command.constant.ParamsConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.SourceTypeConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TangdouzSourceImpl extends SimpleSourceImpl {
    @Override
    String getUrl() {
        return "http://112.124.10.57:8090/";
    }

    @Override
    List<String> getImageUrlsFromResp(String resp) {

        if (resp == null) {
            return new ArrayList<>();
        }
        List<String> ret = new ArrayList<>();
        String[] arr = resp.split("±");
        for (String tmp:arr) {
            if(tmp.contains("img=")) {
                ret.add(tmp.replace("img=", ""));
            }
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return ret;
    }

    @Override
    public String getType() {
        return SourceTypeConstant.TDZ;
    }

    @Override
    public Map<String, Object> standardParams(Map<String, Object> params) {
        params.remove(ParamsConstant.NUM);
        return params;
    }
}

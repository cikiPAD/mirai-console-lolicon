package io.github.samarium150.mirai.plugin.lolicon.command.impl;

import com.google.gson.Gson;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.ParamsConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.SourceTypeConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TangdouzSourceImpl extends SimpleSourceImpl {
    @Override
    String getUrl() {
        return "http://api.tangdouz.com/hlxmt.php";
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

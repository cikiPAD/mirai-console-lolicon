package io.github.samarium150.mirai.plugin.lolicon.command.impl;

import com.google.gson.Gson;
import io.github.samarium150.mirai.plugin.lolicon.command.ImageSourceInterface;
import io.github.samarium150.mirai.plugin.lolicon.command.LoliHttpClient;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.ParamsConstant;
import io.github.samarium150.mirai.plugin.lolicon.command.constant.SourceTypeConstant;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NyanSourceImpl implements ImageSourceInterface {


    public String url = "https://sex.nyan.xyz/api/v2/";

    @Override
    public String getType() {
        return SourceTypeConstant.NYAN;
    }

    @Override
    public List<InputStream> getImageStream(Map<String, Object> params) {
        throw new UnsupportedOperationException("不支持此操作");
    }


    @Override
    public List<String> getImageUrl(Map<String, Object> params) {
        String url = handleReqUrl(params);
        String s = LoliHttpClient.get(url, null, null);
        if (s == null) {
            return new ArrayList<>();
        }
        Gson gson = new Gson();
        Map map = gson.fromJson(s, Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) map.get("data");

        List<String> ret = new ArrayList<>();
        for (Map<String, Object> one:data) {
            String imageUrl = (String) one.get("url");
            ret.add(imageUrl);
        }
        return handleRespUrl(ret, params);
    }

    @Override
    public Map<String, Object> standardParams(Map<String, Object> params) {
        if (params.containsKey(ParamsConstant.TAG)) {
            params.put(ParamsConstant.NYAN_KEYWORD, params.get(ParamsConstant.TAG));
            params.remove(ParamsConstant.TAG);
        }

        if (params.containsKey(ParamsConstant.R18)) {
            int r18 = (int) params.get(ParamsConstant.R18);
            if (r18 == 1) {
                params.put(ParamsConstant.R18, true);
            }
            else {
                params.put(ParamsConstant.R18, false);
            }
        }


        return params;
    }


    public List<String> handleRespUrl(List<String> urls,Map<String, Object> params) {
        if (params.containsKey(ParamsConstant.SIZE)) {
            String size = (String) params.get(ParamsConstant.SIZE);
            if (ParamsConstant.ORIGINAL_SIZE.equalsIgnoreCase(size)) {
                return urls;
            }
            else {
                List<String> ret = new ArrayList<>();
                for (String url:urls) {
                    String tmp = url.replace("img-original", "img-master").replace("_p0.","_p0_master1200.");
                    tmp = tmp.substring(0, tmp.lastIndexOf("."));
                    tmp = tmp + ".jpg";
                    ret.add(tmp);
                }
                return ret;
            }
        }
        return urls;

    }


    public String handleReqUrl(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(url).append("?").append(generateUrlParams(standardParams(params)));
        return sb.toString();
    }


    public static String generateUrlParams(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof List) {
                List<?> listValue = (List<?>) value;
                for (Object item : listValue) {
                    sb.append(encodeUrlParam(key, item));
                    sb.append("&");
                }
            } else {
                sb.append(encodeUrlParam(key, value));
                sb.append("&");
            }
        }

        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1); // Remove the trailing '&'
        }

        return sb.toString();
    }

    private static String encodeUrlParam(String key, Object value) {
        try {
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            String encodedValue = URLEncoder.encode(value.toString(), "UTF-8");
            return encodedKey + "=" + encodedValue;
        } catch (UnsupportedEncodingException e) {
            // Handle encoding exception as per your requirement
            e.printStackTrace();
            return "";
        }
    }


}

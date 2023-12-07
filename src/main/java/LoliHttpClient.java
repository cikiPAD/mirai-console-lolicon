package io.github.samarium150.mirai.plugin.lolicon.command;


import org.apache.http.Header;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Map;


/**
 * HttpClient
 *
 * @author hekm
 * @date 2017-6-2
 *
 */
public class LoliHttpClient {

    public static String postForBody(String url, String json,Map<String,String> headerMap) {

        CloseableHttpResponse response = null;
        String content = null;
        CloseableHttpClient httpclient = HttpClients.createDefault();
        if(url.startsWith("https")) {
            try {
                httpclient = new SSLClient();
            } catch (Exception e) {

            }
        }
        try {

            HttpPost httppost = new HttpPost(url);
            //httppost.addHeader("Content-Type","application/json;charset=UTF-8");
            if(null != headerMap) {
                for(String key : headerMap.keySet()) {
                    httppost.addHeader(key, headerMap.get(key));
                }
            }

            if(null != json && !"".equals(json.trim())) {
                StringEntity se = new StringEntity(json,"utf-8");
                httppost.setEntity(se);
            }
            response = httpclient.execute(httppost);

            content = getBody(response);

        } catch (ClientProtocolException e) {
        } catch (IOException e) {
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
            }
        }
        return content;
    }


    public static String getBody(CloseableHttpResponse response) {
        String content = null;
        if(null != response.getEntity()) {
            try {
                content = EntityUtils.toString(response.getEntity());
                return content;
            }catch (Exception e) {
            }
        }

        return content;
    }

    public static String get(String url,String authorizationKeyName,String authorization) {
        String content = null;
        CloseableHttpClient httpclient = HttpClients.createDefault();
        if(url.startsWith("https")) {
            try {
                httpclient = new SSLClient();
            } catch (Exception e) {
            }
        }
        try {

            HttpGet httpget = new HttpGet(url);
            if(null != authorization) {
                httpget.addHeader(authorizationKeyName, authorization);
            }
            CloseableHttpResponse response = httpclient.execute(httpget);
            content = getBody(response);
        } catch (ClientProtocolException e) {
        } catch (IOException e) {
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
            }
        }
        return content;
    }


    public static String get(String url,Map<String, String> headers) {
        String content = null;
        CloseableHttpClient httpclient = HttpClients.createDefault();
        if(url.startsWith("https")) {
            try {
                httpclient = new SSLClient();
            } catch (Exception e) {
            }
        }
        try {

            HttpGet httpget = new HttpGet(url);
            for (String headerkey:headers.keySet()) {
                httpget.addHeader(headerkey, headers.get(headerkey));
            }

            CloseableHttpResponse response = httpclient.execute(httpget);
            content = getBody(response);
        } catch (ClientProtocolException e) {
        } catch (IOException e) {
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
            }
        }
        return content;
    }


}

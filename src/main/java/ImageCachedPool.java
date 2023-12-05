package io.github.samarium150.mirai.plugin.lolicon.command;


import com.google.gson.Gson;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;


public class ImageCachedPool extends Thread {

    public volatile boolean started = false;

    public volatile boolean isActiveNow = false;

    public volatile boolean isRunning = true;

    public String reqJson = "{}";

    public String url = "https://api.lolicon.app/setu/v2";

    private static int size = 50;
    private static String storagePath = "/root/mirai/imageCache/";
    private static BlockingQueue<String> files = new LinkedBlockingDeque<>(size);
    public static ImageCachedPool instance = new ImageCachedPool();


    private ImageCachedPool() {
    }

    static {
        checkDirExist(storagePath);
    }

    public InputStream getImage() throws InterruptedException, FileNotFoundException {
        String path = files.poll(60, TimeUnit.SECONDS);
        if (path != null) {
            return new FileInputStream(path);
        }
        else {
            return null;
        }
    }

    public double getSizePer() {
        return files.size()/size;
    }

    public void putImage(String url) throws InterruptedException {
        //不要爬太快
        Thread.sleep(2000);
        if (url == null || url.length() == 0) {
            return;
        }

        InputStream is = null;
        OutputStream os = null;
        try {
            URL u = new URL(url);
            URLConnection connection = u.openConnection();
            connection.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            is = connection.getInputStream();
            String suffix = url.substring(url.lastIndexOf("."), url.length());
            String fileName = storagePath + "" + System.currentTimeMillis() + suffix;
            os = new FileOutputStream(fileName);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            files.put(fileName);

        }
        catch (Exception e) {
            System.out.println(e);
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private static void checkDirExist(String strLocal)
    {
        File file = new File(strLocal);
        if (!file.exists() && !file.isDirectory())
        {
            file.mkdirs();
        }
    }

    public static void setPath(String path) {
        storagePath = path;
        checkDirExist(path);
    }


    public void startRun() {
        if (started) {
            return;
        }
        else {
            started = true;
            this.start();
        }
    }

    public void stopRun() {
        isActiveNow = false;
    }


    public void shutdown() {
        isRunning = false;
    }

    public void changeReq(String req) {
        this.reqJson = req;
    }


    @Override
    public void run() {

        while(isRunning) {

            try {
                while (isActiveNow) {
                    Thread.sleep(1000);
                    Map<String, String> header = new HashMap<>();
                    header.put("Content-Type", "application/json");
                    String s = LoliHttpClient.postForBody(url, reqJson, header);
                    if (s == null) {
                        Thread.sleep(5000);
                        continue;
                    }
                    Gson gson = new Gson();
                    Map map = gson.fromJson(s, Map.class);
                    List<Map<String, Map<String, Object>>> data = (List<Map<String, Map<String, Object>>>) map.get("data");

                    for (Map<String, Map<String, Object>> one:data) {
                        Map<String, Object> urlsData = one.get("urls");
                        for (String key :urlsData.keySet()) {
                            putImage((String) urlsData.get(key));
                        }
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

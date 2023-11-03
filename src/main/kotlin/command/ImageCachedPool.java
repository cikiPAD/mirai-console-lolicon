package test;


import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class ImageCachedPool {
    private static int size = 30;
    private static String storagePath = "/home/mirai/imageCache/";
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

                }
            }

            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {

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

}

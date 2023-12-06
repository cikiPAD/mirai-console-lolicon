package io.github.samarium150.mirai.plugin.lolicon.command;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface ImageSourceInterface {
    List<InputStream> getImageStream(Map<String, Object> params);

    String getType();

    List<String> getImageUrl(Map<String, Object> params);

    Map<String, Object> standardParams(Map<String, Object> params);
}
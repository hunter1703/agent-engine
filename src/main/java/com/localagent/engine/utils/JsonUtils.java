package com.localagent.engine.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JsonUtils {
    private JsonUtils(){}

    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        if (map == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(map), clazz);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, typeReference.getType());
    }

    public static Map<String, Object> toMap(InputStream is) {
        if (is == null) {
            return null;
        }
        return JSON.parseObject(is, new TypeReference<>() {
        }.getType());
    }

    public static <T> T fromFile(Path path, Class<T> clazz) {
        try {
            return JSON.parseObject(Files.newInputStream(path), clazz);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T> T fromFile(Path path, TypeReference<T> typeReference) {
        try {
            return JSON.parseObject(Files.newInputStream(path), typeReference.getType());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String toJson(Object value) {
        return JSON.toJSONString(value);
    }
}

package com.agentengine.engine.api.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONPath;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.alibaba.fastjson2.filter.PropertyFilter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class JsonUtils {
  private JsonUtils() {
  }

  public static <T> T fromMap(final Map<String, Object> map, final Class<T> clazz) {
    if (map == null) {
      return null;
    }
    return JSON.parseObject(JSON.toJSONString(map), clazz);
  }

  public static <T> T fromJson(final String json, final Class<T> clazz) {
    if (json == null || json.isBlank()) {
      return null;
    }
    return JSON.parseObject(json, clazz);
  }

  public static <T> T fromJson(final String json, final TypeReference<T> typeReference) {
    if (json == null || json.isBlank()) {
      return null;
    }
    return JSON.parseObject(json, typeReference.getType());
  }

  public static Map<String, Object> toMap(final InputStream is) {
    if (is == null) {
      return null;
    }
    return JSON.parseObject(is, new TypeReference<>() {
    }.getType());
  }

  public static Map<String, Object> toMap(final Object obj) {
    if (obj == null) {
      return null;
    }
    // noinspection unchecked
    return (Map<String, Object>) JSON.toJSON(obj);
  }

  public static <T> T fromFile(final Path path, final Class<T> clazz) {
    try (InputStream stream = Files.newInputStream(path)) {
      return JSON.parseObject(stream, clazz);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static <T> T fromFile(final Path path, final TypeReference<T> typeReference) {
    try (InputStream stream = Files.newInputStream(path)) {
      return JSON.parseObject(stream, typeReference.getType());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static String toJson(final Object value) {
    return JSON.toJSONString(value, (PropertyFilter) (_, _, v) -> {
      if (v == null) {
        return false;
      }
      if (v instanceof Optional<?> optional) {
        return optional.isPresent();
      }
      return true;
    });
  }

  public static void removeValue(Object jsonObject, String key) {
    if (jsonObject == null) {
      return;
    }
    JSONPath.of(key).remove(jsonObject);
  }

  public static String toStableJson(final Object value) {
    return JSON.toJSONString(value, JSONWriter.Feature.SortMapEntriesByKeys);
  }
}

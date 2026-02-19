package com.agentengine.engine.api.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.adk.JsonBaseModel;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JsonUtils {
  private static final ObjectMapper STABLE_MAPPER = JsonBaseModel.getMapper().copy()
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private JsonUtils() {
  }

  public static <T> T fromMap(final Map<String, Object> map, final Class<T> clazz) {
    if (map == null) {
      return null;
    }
    return STABLE_MAPPER.convertValue(map, clazz);
  }

  public static <T> T fromJson(final String json, final Class<T> clazz) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return STABLE_MAPPER.readValue(json, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromJson(final String json, final Type type) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return STABLE_MAPPER.readValue(json, STABLE_MAPPER.getTypeFactory().constructType(type));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromStream(final InputStream inputStream, final Class<T> clazz) {
    if (inputStream == null) {
      return null;
    }
    try {
      return STABLE_MAPPER.readValue(inputStream, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromJson(final String json, final TypeReference<T> typeReference) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return STABLE_MAPPER.readValue(json, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> toMap(final InputStream is) {
    if (is == null) {
      return null;
    }
    try {
      return STABLE_MAPPER.readValue(is, new TypeReference<Map<String, Object>>() {
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> toMap(final Object obj) {
    if (obj == null) {
      return null;
    }
    return STABLE_MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() {
    });
  }

  public static Map<String, Object> toJacksonMap(final Object obj) {
    return toMap(obj);
  }

  public static <T> T fromJacksonMap(final Map<String, Object> map, final Class<T> clazz) {
    return fromMap(map, clazz);
  }

  public static <T> T fromFile(final Path path, final Class<T> clazz) {
    try (InputStream stream = Files.newInputStream(path)) {
      return STABLE_MAPPER.readValue(stream, clazz);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static <T> T fromFile(final Path path, final TypeReference<T> typeReference) {
    try (InputStream stream = Files.newInputStream(path)) {
      return STABLE_MAPPER.readValue(stream, typeReference);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static String toJson(final Object value) {
    try {
      return STABLE_MAPPER.writeValueAsString(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void toStream(OutputStream os, Object value) throws IOException {
    STABLE_MAPPER.writeValue(os, value);
  }

  public static String toStableJson(final Object value) {
    try {
      return STABLE_MAPPER.writeValueAsString(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void removeValue(Object jsonObject, String path) {
    JsonPath.using(Configuration.builder().build()).parse(jsonObject).delete(path);
  }
}

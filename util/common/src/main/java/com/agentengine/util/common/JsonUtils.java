package com.agentengine.util.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

  private static final ObjectMapper JSON_MAPPER = createJsonMapper(false);
  private static final ObjectMapper JSON_MAPPER_WITH_TYPE = createJsonMapper(true);

  private JsonUtils() {
  }

  private static ObjectMapper createJsonMapper(boolean includeTypeInfo) {
    ObjectMapper mapper = JsonMapper.builder().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).build();

    if (includeTypeInfo) {
      BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build();

      mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    }

    return mapper;
  }

  public static <T> T fromMap(final Map<String, Object> map, final Class<T> clazz) {
    if (map == null) {
      return null;
    }
    return JSON_MAPPER.convertValue(map, clazz);
  }

  public static <T> T fromMap(final Map<String, Object> map, final TypeReference<T> typeReference) {
    if (map == null) {
      return null;
    }
    return JSON_MAPPER.convertValue(map, typeReference);
  }

  public static <T> T fromJson(final String json, final Class<T> clazz) {
    return fromJson(json, clazz, false);
  }

  public static <T> T fromJson(final String json, final Class<T> clazz, boolean includesTypeInfo) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return mapper(includesTypeInfo).readValue(json, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromJson(final String json, final Type type) {
    return fromJson(json, type, false);
  }

  public static <T> T fromJson(final String json, final Type type, boolean includesTypeInfo) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      ObjectMapper mapper = mapper(includesTypeInfo);
      return mapper.readValue(json, mapper.getTypeFactory().constructType(type));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromJson(final String json, final TypeReference<T> typeReference) {
    return fromJson(json, typeReference, false);
  }

  public static <T> T fromJson(final String json, final TypeReference<T> typeReference, boolean includesTypeInfo) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return mapper(includesTypeInfo).readValue(json, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromStream(final InputStream inputStream, final Class<T> clazz) {
    return fromStream(inputStream, clazz, false);
  }

  public static <T> T fromStream(final InputStream inputStream, final Class<T> clazz, boolean includesTypeInfo) {
    if (inputStream == null) {
      return null;
    }
    try {
      return mapper(includesTypeInfo).readValue(inputStream, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromFile(final Path path, final Class<T> clazz) {
    return fromFile(path, clazz, false);
  }

  public static <T> T fromFile(final Path path, final Class<T> clazz, boolean includesTypeInfo) {
    try (InputStream stream = Files.newInputStream(path)) {
      return mapper(includesTypeInfo).readValue(stream, clazz);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static <T> T fromFile(final Path path, final TypeReference<T> typeReference) {
    return fromFile(path, typeReference, false);
  }

  public static <T> T fromFile(final Path path, final TypeReference<T> typeReference, boolean includesTypeInfo) {
    try (InputStream stream = Files.newInputStream(path)) {
      return mapper(includesTypeInfo).readValue(stream, typeReference);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static String toJson(final Object value) {
    return toJson(value, false);
  }

  public static String toJson(final Object value, boolean includeTypeInfo) {
    if (value == null) {
      return null;
    }
    try {
      return getObjectWriter(includeTypeInfo).writeValueAsString(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void toStream(final OutputStream os, final Object value) throws IOException {
    JSON_MAPPER.writeValue(os, value);
  }

  public static Map<String, Object> toMap(final Object obj) {
    if (obj == null) {
      return null;
    }
    return JSON_MAPPER.convertValue(obj, new TypeReference<>() {
    });
  }

  public static String toStableJson(final Object value) {
    return toJson(value);
  }

  public static void removeValue(final Object jsonObject, final String path) {
    JsonPath.using(Configuration.builder().build()).parse(jsonObject).delete(path);
  }

  public static Map<String, Object> parseJsonPayload(final String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String cleaned = text.trim();
    if (cleaned.startsWith("```")) {
      final int end = cleaned.lastIndexOf("```");
      if (end > 2) {
        cleaned = cleaned.substring(3, end).trim();
        if (cleaned.startsWith("json")) {
          cleaned = cleaned.substring(4).trim();
        }
      }
    }
    Map<String, Object> payload = null;
    try {
      payload = fromJson(cleaned, new TypeReference<>() {
      });
    } catch (Exception ex) {
      final int start = cleaned.indexOf('{');
      final int end = cleaned.lastIndexOf('}');
      if (start >= 0 && end > start) {
        try {
          payload = fromJson(cleaned.substring(start, end + 1), new TypeReference<>() {
          });
        } catch (Exception innerEx) {
          LOGGER.warn("Failed to parse JSON payload from substring", innerEx);
        }
      }
    }
    return payload;
  }

  private static ObjectWriter getObjectWriter(final boolean includeTypeInfo) {
    ObjectMapper mapper = mapper(includeTypeInfo);
    return includeTypeInfo ? mapper.writerFor(Object.class) : mapper.writer();
  }

  private static ObjectMapper mapper(boolean includeTypeInfo) {
    return includeTypeInfo ? JSON_MAPPER_WITH_TYPE : JSON_MAPPER;
  }
}
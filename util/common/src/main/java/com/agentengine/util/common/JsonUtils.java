package com.agentengine.util.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);
  private static final ObjectMapper JSON_MAPPER = JsonBaseModel.getMapper().copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
      true);
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private JsonUtils() {
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
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return JSON_MAPPER.readValue(json, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromYaml(final String yaml, final Class<T> clazz) {
    if (yaml == null || yaml.isBlank()) {
      return null;
    }
    try {
      return YAML_MAPPER.readValue(yaml, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromJson(final String json, final Type type) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return JSON_MAPPER.readValue(json, JSON_MAPPER.getTypeFactory().constructType(type));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromStream(final InputStream inputStream, final Class<T> clazz) {
    if (inputStream == null) {
      return null;
    }
    try {
      return JSON_MAPPER.readValue(inputStream, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromJson(final String json, final TypeReference<T> typeReference) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return JSON_MAPPER.readValue(json, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> toMap(final InputStream is) {
    if (is == null) {
      return null;
    }
    try {
      return JSON_MAPPER.readValue(is, new TypeReference<Map<String, Object>>() {
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> toMap(final Object obj) {
    if (obj == null) {
      return null;
    }
    return JSON_MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() {
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
      return JSON_MAPPER.readValue(stream, clazz);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static <T> T fromFile(final Path path, final TypeReference<T> typeReference) {
    try (InputStream stream = Files.newInputStream(path)) {
      return JSON_MAPPER.readValue(stream, typeReference);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static String toJson(final Object value) {
    try {
      return JSON_MAPPER.writeValueAsString(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void toStream(final OutputStream os, final Object value) throws IOException {
    JSON_MAPPER.writeValue(os, value);
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
}

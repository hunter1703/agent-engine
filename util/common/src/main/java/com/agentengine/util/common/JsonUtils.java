package com.agentengine.util.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

  private static final JsonMapper JSON_MAPPER = createJsonMapper(JsonMapper.builder());

  private JsonUtils() {}

  private static JsonMapper createJsonMapper(final JsonMapper.Builder builder) {
    final JsonMapper mapper =
        builder
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .addModule(new Jdk8Module())
            .addModule(new JavaTimeModule())
            .addModule(new GuavaModule())
            .serializationInclusion(JsonInclude.Include.NON_ABSENT)
            .build();
    // Builder setters for Optional fields do Optional.of(requireNonNull(value)), which NPEs on a
    // null JSON value; skipping the setter call instead leaves the builder default
    // (Optional.empty).
    mapper.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP));
    return mapper;
  }

  /** An independent copy of the default mapper */
  public static ObjectMapper copyMapper() {
    return JSON_MAPPER.copy();
  }

  public static ObjectMapper copyMapper(final JsonFactory jsonFactory) {
    return createJsonMapper(JsonMapper.builder(jsonFactory));
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
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  public static <T> T fromJson(final String json, final Type type) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return JSON_MAPPER.readValue(json, JSON_MAPPER.getTypeFactory().constructType(type));
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  public static <T> T fromJson(final String json, final TypeReference<T> typeReference) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return JSON_MAPPER.readValue(json, typeReference);
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  public static <T> T fromStream(final InputStream inputStream, final Class<T> clazz) {
    if (inputStream == null) {
      return null;
    }
    try {
      return JSON_MAPPER.readValue(inputStream, clazz);
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
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
    if (value == null) {
      return null;
    }
    try {
      return JSON_MAPPER.writer().writeValueAsString(value);
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  public static void toStream(final OutputStream os, final Object value) throws IOException {
    JSON_MAPPER.writeValue(os, value);
  }

  public static Map<String, Object> toMap(final Object obj) {
    if (obj == null) {
      return null;
    }
    return JSON_MAPPER.convertValue(obj, new TypeReference<>() {});
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
      payload = fromJson(cleaned, new TypeReference<>() {});
    } catch (Exception ex) {
      final int start = cleaned.indexOf('{');
      final int end = cleaned.lastIndexOf('}');
      if (start >= 0 && end > start) {
        try {
          payload = fromJson(cleaned.substring(start, end + 1), new TypeReference<>() {});
        } catch (Exception innerEx) {
          LOGGER.warn("Failed to parse JSON payload from substring", innerEx);
        }
      }
    }
    return payload;
  }

  public static JsonNode toJsonNode(final Object object) {
    if (object == null) {
      return null;
    }

    return JSON_MAPPER.valueToTree(object);
  }

  public static JsonNode toJsonNode(final String json) {
    if (StringUtils.isBlank(json)) {
      return null;
    }

    try {
      return JSON_MAPPER.readTree(json);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }
}

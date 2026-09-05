package com.agentengine.util.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.Objects;

@Singleton
public class JsonCodec {

  private final ObjectMapper mapper;

  @Inject
  public JsonCodec(final Instance<JsonCodecFactory> factories) {
    this.mapper =
        factories.stream()
            .sorted(Comparator.comparingInt(JsonCodecFactory::priority))
            .map(JsonCodecFactory::getCodec)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No JsonCodecFactory produced a codec"));
  }

  public String serialize(final Object value) {
    return serialize(value, false);
  }

  public String serialize(final Object value, final boolean includeTypeInfo) {
    if (value == null) {
      return null;
    }
    if (includeTypeInfo) {
      return JsonUtils.toJson(value, true);
    }
    try {
      return mapper.writeValueAsString(value);
    } catch (final JsonProcessingException exception) {
      throw new RuntimeException(exception);
    }
  }

  public void writeTo(final OutputStream out, final Object value) {
    if (value == null) {
      return;
    }
    try {
      mapper.writeValue(out, value);
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  public <T> T deserialize(final String json, final Class<T> type) {
    return deserialize(json, (Type) type);
  }

  public <T> T deserialize(final String json, final Type type) {
    return deserialize(json, type, false);
  }

  public <T> T deserialize(final String json, final Type type, final boolean includeTypeInfo) {
    if (json == null || json.isBlank()) {
      return null;
    }
    if (includeTypeInfo) {
      return JsonUtils.fromJson(json, type, true);
    }
    try {
      return mapper.readValue(json, mapper.getTypeFactory().constructType(type));
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  public <T> T deserialize(final InputStream in, final Class<T> type) {
    return deserialize(in, (Type) type);
  }

  public <T> T deserialize(final InputStream in, final Type type) {
    try {
      return mapper.readValue(in, mapper.getTypeFactory().constructType(type));
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }
}

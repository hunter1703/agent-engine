package com.agentengine.util.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public abstract class JsonCodec {

  private final ObjectMapper mapper;

  public JsonCodec(final Instance<CodecModuleProvider> providers) {
    this.mapper = buildMapper(providers);
  }

  protected abstract ObjectMapper buildMapper(Instance<CodecModuleProvider> providers);

  public <T> T convertValue(final Object value, final Type type) {
    return mapper.convertValue(value, mapper.getTypeFactory().constructType(type));
  }

  public String serialize(final Object value) {
    if (value == null) {
      return null;
    }
    try {
      return mapper.writeValueAsString(value);
    } catch (final JsonProcessingException exception) {
      throw new RuntimeException(exception);
    }
  }

  /**
   * Declares the batch's element type as {@code itemType} instead of bare {@code Object} — every
   * polymorphic type in this codebase carries its own {@code @JsonTypeInfo} discriminator (e.g.
   * {@code Event}'s {@code "type"} field), and Jackson only consults that when it matches the
   * *declared* type being serialized, not just the runtime type. Declaring the list as {@code
   * List<Object>} would make every element's declared type plain {@code Object}, losing that.
   */
  public String serialize(final List<?> batch, final Class<?> itemType) {
    if (batch == null) {
      return null;
    }
    try {
      return mapper
          .writerFor(mapper.getTypeFactory().constructArrayType(itemType))
          .writeValueAsString(batch.toArray());
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
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return mapper.readValue(json, mapper.getTypeFactory().constructType(type));
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  public <T> T deserialize(final InputStream inputStream, final Class<T> type) {
    return deserialize(inputStream, (Type) type);
  }

  public <T> T deserialize(final InputStream inputStream, final Type type) {
    try {
      return mapper.readValue(inputStream, mapper.getTypeFactory().constructType(type));
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  public List<String> splitJsonArray(final String json) {
    if (StringUtils.isBlank(json)) {
      return List.of();
    }
    try {
      final JsonNode root = mapper.readTree(json);
      if (!root.isArray()) {
        return List.of(json);
      }
      final List<String> elements = new ArrayList<>(root.size());
      for (final JsonNode element : root) {
        elements.add(mapper.writeValueAsString(element));
      }
      return elements;
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }
}

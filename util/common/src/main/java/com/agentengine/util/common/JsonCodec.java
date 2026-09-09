package com.agentengine.util.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public abstract class JsonCodec {

  private final ObjectMapper mapper;

  public JsonCodec(final List<CodecModuleProvider> providers) {
    this.mapper = buildMapper(providers);
  }

  protected abstract ObjectMapper buildMapper(List<CodecModuleProvider> providers);

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
      throw ExceptionUtils.wrapInRuntimeException(exception);
    }
  }

  public String serializeBatch(final List<?> batch) {
    if (batch == null) {
      return null;
    }
    try {
      final StringWriter writer = new StringWriter();
      try (final SequenceWriter seq = mapper.writer().writeValuesAsArray(writer)) {
        for (final Object element : batch) {
          seq.write(element);
        }
      }
      return writer.toString();
    } catch (final IOException exception) {
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

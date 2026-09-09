package com.agentengine.util.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

  public String serializeBatch(final List<?> batch, final Type elementType) {
    if (batch == null) {
      return null;
    }
    final StringBuilderWriter writer = new StringBuilderWriter();

    try {
      final ObjectWriter typeWriter =
          mapper.writerFor(mapper.getTypeFactory().constructType(elementType));
      try (final JsonGenerator gen = mapper.getFactory().createGenerator(writer)) {
        gen.writeStartArray();
        for (final Object element : batch) {
          typeWriter.writeValue(gen, element);
        }
        gen.writeEndArray();
      }
      return writer.toString();
    } catch (final IOException exception) {
      throw ExceptionUtils.wrapInRuntimeException(exception);
    }
  }

  public String serializeBatch(final Object[] args, final Type[] types) {
    if (args == null) {
      return null;
    }
    final StringBuilderWriter writer = new StringBuilderWriter();

    try {
      try (final JsonGenerator gen = mapper.getFactory().createGenerator(writer)) {
        gen.writeStartArray();
        for (int i = 0; i < args.length; i++) {
          if (types != null && i < types.length && types[i] != null) {
            mapper
                .writerFor(mapper.getTypeFactory().constructType(types[i]))
                .writeValue(gen, args[i]);
          } else {
            mapper.writeValue(gen, args[i]);
          }
        }
        gen.writeEndArray();
      }
      return writer.toString();
    } catch (final IOException exception) {
      throw ExceptionUtils.wrapInRuntimeException(exception);
    }
  }

  public Object[] deserializeBatch(final String json, final Type[] types) {
    if (StringUtils.isBlank(json)) {
      return null;
    }
    if (types == null) {
      throw new IllegalArgumentException("Types list cannot be null");
    }

    try (final JsonParser parser = mapper.getFactory().createParser(json)) {
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        throw new IllegalArgumentException("Expected JSON array");
      }

      final Object[] result = new Object[types.length];
      for (int i = 0; i < types.length; i++) {
        final Type type = types[i];
        if (parser.nextToken() == JsonToken.END_ARRAY) {
          break;
        }
        result[i] = mapper.readValue(parser, mapper.getTypeFactory().constructType(type));
      }

      return result;
    } catch (final IOException exception) {
      throw ExceptionUtils.wrapInRuntimeException(exception);
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

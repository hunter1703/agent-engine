package com.agentengine.util.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;

@Singleton
public class JsonCodec {

  private final ObjectMapper mapper;
  private final ObjectMapper typedMapper;

  @Inject
  public JsonCodec(final Instance<CodecModuleProvider> providers) {
    this.mapper = buildMapper(providers, false);
    this.typedMapper = buildMapper(providers, true);
  }

  private static ObjectMapper buildMapper(
      final Instance<CodecModuleProvider> providers, final boolean includeTypeInfo) {
    final ObjectMapper built = JsonUtils.copyMapper();
    for (final CodecModuleProvider provider : providers) {
      final Module module = provider.getModule(includeTypeInfo);
      if (module != null) {
        built.registerModule(module);
      }
    }
    return built;
  }

  public String serialize(final Object value) {
    return serialize(value, false);
  }

  /**
   * {@code includeTypeInfo}: for a heterogeneous collection with no single shared class, where the
   * receiver needs each element's concrete type embedded in the JSON itself to resolve it.
   */
  public String serialize(final Object value, final boolean includeTypeInfo) {
    if (value == null) {
      return null;
    }
    try {
      return includeTypeInfo
          ? typedMapper.writerFor(Object.class).writeValueAsString(value)
          : mapper.writeValueAsString(value);
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
    try {
      final ObjectMapper effective = includeTypeInfo ? typedMapper : mapper;
      return effective.readValue(json, effective.getTypeFactory().constructType(type));
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

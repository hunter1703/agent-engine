package com.agentengine.util.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A class needs its own {@code @JsonTypeInfo} (class-level if we own it, a mixin registered via
 * {@link CodecModuleProvider} if it's a library type) only when both hold:
 *
 * <ul>
 *   <li>it's non-final -- has, or could plausibly get, a subclass;
 *   <li>it's used as a <i>declared</i> type somewhere in a codec's real surface -- a field type,
 *       method return/parameter type, or resolved generic argument. A non-final class that's always
 *       referenced by its own concrete type, never through a shared supertype, has no ambiguity for
 *       Jackson to resolve, so annotating it does nothing.
 * </ul>
 *
 * <p>Compare {@code Map<String, Object>} vs. {@code Map<String, Parent>}, both holding a {@code
 * Child extends Parent} value:
 *
 * <ul>
 *   <li>{@code Map<String, Object>} -- the declared value type is {@code Object} itself, not a real
 *       class, so there's nothing to annotate. This is what each subclass's own narrow
 *       default-typing module ({@link ObjectTypingModule}'s {@code JAVA_LANG_OBJECT}) exists to
 *       handle: it tags based on the slot being exactly {@code Object}, automatically.
 *   <li>{@code Map<String, Parent>} -- the declared value type is {@code Parent}, a real class that
 *       differs from the runtime value's class ({@code Child}). {@code JAVA_LANG_OBJECT} does
 *       <b>not</b> tag this slot (it isn't declared {@code Object}), so {@code Parent} itself needs
 *       the {@code @JsonTypeInfo}. Annotating {@code Child} instead would do nothing -- Jackson
 *       reads the type-info config from the <i>declared</i> type's annotations (or its mixin), not
 *       the runtime value's.
 * </ul>
 */
public abstract class JsonCodec {

  private final ObjectMapper mapper;

  public JsonCodec(final List<CodecModuleProvider> providers) {
    this.mapper = buildMapper(providers);
  }

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

  /**
   * Serializes against the given declared type rather than {@code value}'s own runtime class — so
   * default typing's decision to tag with {@code @class} is based on the declared type (e.g. the
   * method's return type), not whichever concrete, possibly-final class the value happens to be
   * (e.g. {@code List.of(...)}'s package-private final impl), which a reader expecting the declared
   * type to be tagged has no way to know about.
   */
  public String serialize(final Object value, final Type type) {
    if (value == null) {
      return null;
    }
    try {
      return mapper
          .writerFor(mapper.getTypeFactory().constructType(type))
          .writeValueAsString(value);
    } catch (final JsonProcessingException exception) {
      throw ExceptionUtils.wrapInRuntimeException(exception);
    }
  }

  /**
   * Same as {@link #serialize(Object, Type)} but writes UTF-8 bytes directly to {@code out} instead
   * of building a {@code String} first -- see {@link #serializeBatch(List, Type, OutputStream)} for
   * why this matters on a hot path.
   */
  public void serialize(final Object value, final Type type, final OutputStream out) {
    if (value == null) {
      return;
    }
    try {
      mapper.writerFor(mapper.getTypeFactory().constructType(type)).writeValue(out, value);
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
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

  /**
   * Same as {@link #serializeBatch(List, Type)} but writes UTF-8 bytes directly to {@code out}
   * instead of building a {@code String} first -- Jackson's stream-based {@link JsonGenerator}
   * skips the char[]/UTF-16 intermediate that {@code writeValueAsString(...).getBytes(UTF_8)} would
   * otherwise require, which matters on a hot request path (confirmed via JFR profiling: this
   * string encode/decode round trip was ~35% of sampled CPU on the {@code agent} service).
   */
  public void serializeBatch(final List<?> batch, final Type elementType, final OutputStream out) {
    if (batch == null) {
      return;
    }
    try {
      final ObjectWriter typeWriter =
          mapper.writerFor(mapper.getTypeFactory().constructType(elementType));
      try (final JsonGenerator gen = mapper.getFactory().createGenerator(out)) {
        gen.writeStartArray();
        for (final Object element : batch) {
          typeWriter.writeValue(gen, element);
        }
        gen.writeEndArray();
      }
    } catch (final IOException exception) {
      throw ExceptionUtils.wrapInRuntimeException(exception);
    }
  }

  public void serializeBatchNdjson(
      final List<?> batch, final Type elementType, final OutputStream out) {
    if (batch == null || batch.isEmpty()) {
      return;
    }
    try {
      final ObjectWriter typeWriter =
          mapper.writerFor(mapper.getTypeFactory().constructType(elementType));
      try (final JsonGenerator gen = mapper.getFactory().createGenerator(out)) {
        boolean first = true;
        for (final Object element : batch) {
          if (!first) {
            gen.writeRaw('\n');
          }
          first = false;
          typeWriter.writeValue(gen, element);
        }
      }
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

  /**
   * Same as {@link #serializeBatch(Object[], Type[])} but writes UTF-8 bytes directly to {@code
   * out} instead of building a {@code String} first -- see {@link #serializeBatch(List, Type,
   * OutputStream)} for why this matters on a hot path.
   */
  public void serializeBatch(final Object[] args, final Type[] types, final OutputStream out) {
    if (args == null) {
      return;
    }
    try {
      try (final JsonGenerator gen = mapper.getFactory().createGenerator(out)) {
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

  /**
   * Reads a {@link #serializeBatchNdjson(List, Type, OutputStream)} stream back: repeatedly
   * advances the same parser and reads one value at a time, since each value's own grammar already
   * marks its end -- no array wrapper or count is needed to know where one stops and the next
   * starts.
   */
  public List<Object> deserializeBatchNdjson(
      final InputStream inputStream, final Type elementType) {
    final List<Object> result = new ArrayList<>();
    try (final JsonParser parser = mapper.getFactory().createParser(inputStream)) {
      final var javaType = mapper.getTypeFactory().constructType(elementType);
      while (parser.nextToken() != null) {
        result.add(mapper.readValue(parser, javaType));
      }
      return result;
    } catch (final IOException exception) {
      throw ExceptionUtils.wrapInRuntimeException(exception);
    }
  }

  /**
   * Splits a {@link #serializeBatchNdjson(List, Type, OutputStream)} payload back into each
   * element's raw bytes, by scanning for the raw {@code '\n'} separator directly -- no Jackson
   * involved, unlike {@link #splitJsonArray(byte[])}, since a newline-delimited payload carries no
   * brackets or commas to tokenize around.
   */
  public List<byte[]> splitNdjson(final byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return List.of();
    }
    final List<byte[]> elements = new ArrayList<>();
    int start = 0;
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == '\n') {
        elements.add(Arrays.copyOfRange(bytes, start, i));
        start = i + 1;
      }
    }
    elements.add(Arrays.copyOfRange(bytes, start, bytes.length));
    return elements;
  }

  /**
   * Same as {@link #deserializeBatch(String, Type[])} but reads UTF-8 bytes directly from {@code
   * inputStream} instead of requiring a pre-decoded {@code String} -- see {@link
   * #serializeBatch(List, Type, OutputStream)} for why this matters on a hot path.
   */
  public Object[] deserializeBatch(final InputStream inputStream, final Type[] types) {
    if (types == null) {
      throw new IllegalArgumentException("Types list cannot be null");
    }

    try (final JsonParser parser = mapper.getFactory().createParser(inputStream)) {
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

  /**
   * Splits a top-level JSON array into each element's own raw JSON text, without parsing into a
   * tree or re-serializing -- each element's substring in {@code json} is already valid JSON, so
   * rebuilding it via Jackson would only add cost. Uses the mapper's own {@link JsonParser} purely
   * for token boundaries (letting Jackson's own tokenizer -- not reimplemented logic -- handle
   * strings/escapes/nesting correctly), then slices the matching span directly out of the original
   * text via {@link JsonLocation#getCharOffset()}. {@link JsonParser#getText()} is required before
   * reading a scalar/string token's end location -- Jackson defers actually scanning a token's text
   * until it's asked for, so the location right after {@code nextToken()} on e.g. a string can
   * point just past its opening quote, not its end (verified empirically, not assumed).
   *
   * <p>A zero-copy {@code CharBuffer.wrap(json, start, end)} variant was tried in place of {@link
   * String#substring} here, but showed no measurable benefit under profiling/benchmarking -- kept
   * this simpler version rather than the added complexity for an unproven gain.
   */
  public List<String> splitJsonArray(final String json) {
    if (StringUtils.isBlank(json)) {
      return List.of();
    }
    try (JsonParser parser = mapper.getFactory().createParser(json)) {
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        return List.of(json);
      }
      final List<String> elements = new ArrayList<>();
      JsonToken token;
      while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
        final JsonLocation start = parser.currentTokenLocation();
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
          parser.skipChildren();
        } else {
          parser.getText();
        }
        final long end = parser.currentLocation().getCharOffset();
        elements.add(json.substring((int) start.getCharOffset(), (int) end));
      }
      return elements;
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  /**
   * Same as {@link #splitJsonArray(String)} but reads UTF-8 bytes directly instead of requiring a
   * pre-decoded {@code String} -- for a byte-native source (a gRPC {@code ByteString}'s bytes),
   * decoding the whole payload into a {@code String} first only to re-scan it would reintroduce
   * exactly the wasted round trip {@link #serializeBatch(List, Type, OutputStream)} avoids on the
   * write side. JSON's structural characters (brackets, braces, comma, quote) are all single-byte
   * ASCII, so a byte offset from {@link JsonLocation#getByteOffset()} slices the same span a char
   * offset would -- no decoding is needed to find element boundaries, only to materialize each
   * element's text, which callers do lazily (or not at all, for a raw passthrough).
   */
  public List<byte[]> splitJsonArray(final byte[] json) {
    if (json == null || json.length == 0) {
      return List.of();
    }
    try (JsonParser parser = mapper.getFactory().createParser(json)) {
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        return List.of(json);
      }
      final List<byte[]> elements = new ArrayList<>();
      JsonToken token;
      while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
        final JsonLocation start = parser.currentTokenLocation();
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
          parser.skipChildren();
        } else {
          parser.getText();
        }
        final long end = parser.currentLocation().getByteOffset();
        elements.add(Arrays.copyOfRange(json, (int) start.getByteOffset(), (int) end));
      }
      return elements;
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  protected abstract ObjectMapper buildMapper(List<CodecModuleProvider> providers);
}

package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.JsonCodec;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import org.jboss.resteasy.reactive.server.spi.ServerMessageBodyWriter;
import org.jboss.resteasy.reactive.server.spi.ServerRequestContext;

/**
 * Implements RESTEasy Reactive's own {@link ServerMessageBodyWriter} SPI, not the plain JAX-RS
 * {@code MessageBodyWriter}: a plain JAX-RS provider doesn't hook into RESTEasy Reactive's direct
 * write path ({@link #writeResponse}), which is what actually serializes each element of a {@code
 * &#64;RestStreamElementType} SSE stream — Quarkus's own built-in Jackson writer (with its own,
 * separately-configured {@code ObjectMapper}) was winning that path instead of this class, even
 * though this class was correctly selected for ordinary (non-streaming) JSON responses.
 */
@Provider
@Produces({MediaType.APPLICATION_JSON, "application/*+json"})
public class JsonMessageBodyWriter extends ServerMessageBodyWriter.AllWriteableMessageBodyWriter {

  private final JsonCodec jsonCodec;

  @Inject
  public JsonMessageBodyWriter(final RestJsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
  }

  @Override
  public void writeResponse(
      final Object value, final Type genericType, final ServerRequestContext context)
      throws WebApplicationException, IOException {
    writeValue(value, context.getOrCreateOutputStream());
  }

  @Override
  public void writeTo(
      final Object value,
      final Class<?> type,
      final Type genericType,
      final Annotation[] annotations,
      final MediaType mediaType,
      final MultivaluedMap<String, Object> httpHeaders,
      final OutputStream entityStream) {
    writeValue(value, entityStream);
  }

  private void writeValue(final Object value, final OutputStream entityStream) {
    try {
      if (value instanceof String rawJson) {
        // since this is already a string it is assumed that it is well-formed json string.
        // if below jsonCodec.writeTo is called, it would try serializing again by escaping
        // characters.
        // Writing the bytes directly is what makes it valid JSON again.
        entityStream.write(rawJson.getBytes(StandardCharsets.UTF_8));
      } else {
        jsonCodec.writeTo(entityStream, value);
      }
    } catch (final Exception exception) {
      throw new RuntimeException(
          String.format("Error serializing JSON with Jackson: %s", exception.getMessage()),
          exception);
    }
  }
}

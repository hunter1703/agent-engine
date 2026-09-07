package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.JsonCodec;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveResourceInfo;
import org.jboss.resteasy.reactive.server.spi.ServerMessageBodyReader;
import org.jboss.resteasy.reactive.server.spi.ServerRequestContext;

/**
 * Implements RESTEasy Reactive's own {@link ServerMessageBodyReader} SPI — see {@link
 * JsonMessageBodyWriter} for why the plain JAX-RS {@code MessageBodyReader} isn't enough for this
 * to be reliably picked up over Quarkus's own built-in Jackson provider.
 */
@Provider
@Consumes({MediaType.APPLICATION_JSON, "application/*+json"})
public class JsonMessageBodyReader implements ServerMessageBodyReader<Object> {

  private final JsonCodec jsonCodec;

  @Inject
  public JsonMessageBodyReader(final JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
  }

  @Override
  public boolean isReadable(
      final Class<?> type,
      final Type genericType,
      final ResteasyReactiveResourceInfo lazyMethod,
      final MediaType mediaType) {
    return isJsonCompatible(mediaType);
  }

  @Override
  public boolean isReadable(
      final Class<?> type,
      final Type genericType,
      final Annotation[] annotations,
      final MediaType mediaType) {
    return isJsonCompatible(mediaType);
  }

  private static boolean isJsonCompatible(final MediaType mediaType) {
    return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
        || mediaType.isCompatible(MediaType.valueOf("application/*+json"));
  }

  @Override
  public Object readFrom(
      final Class<Object> type,
      final Type genericType,
      final MediaType mediaType,
      final ServerRequestContext context)
      throws WebApplicationException, IOException {
    return readValue(genericType != null ? genericType : type, context.getInputStream());
  }

  @Override
  public Object readFrom(
      final Class<Object> type,
      final Type genericType,
      final Annotation[] annotations,
      final MediaType mediaType,
      final MultivaluedMap<String, String> httpHeaders,
      final InputStream entityStream) {
    return readValue(genericType != null ? genericType : type, entityStream);
  }

  private Object readValue(final Type genericType, final InputStream entityStream) {
    try {
      return jsonCodec.deserialize(entityStream, genericType);
    } catch (final Exception exception) {
      throw new IllegalArgumentException(
          String.format("Error deserializing JSON with Jackson: %s", exception.getMessage()),
          exception);
    }
  }
}

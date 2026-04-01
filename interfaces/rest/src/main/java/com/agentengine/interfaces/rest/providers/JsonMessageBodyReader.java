package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.JsonUtils;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Consumes({MediaType.APPLICATION_JSON, "application/*+json"})
public class JsonMessageBodyReader implements MessageBodyReader<Object> {

    @Override
    public boolean isReadable(
            final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
                || mediaType.isCompatible(MediaType.valueOf("application/*+json"));
    }

    @Override
    public Object readFrom(
            final Class<Object> type,
            final Type genericType,
            final Annotation[] annotations,
            final MediaType mediaType,
            final MultivaluedMap<String, String> httpHeaders,
            final InputStream entityStream) {
        try {
            return JsonUtils.fromStream(entityStream, type);
        } catch (final Exception exception) {
            throw new IllegalArgumentException(
                    String.format("Error deserializing JSON with Jackson: %s", exception.getMessage()), exception);
        }
    }
}

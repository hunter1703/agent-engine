package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.JsonUtils;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces({MediaType.APPLICATION_JSON, "application/*+json"})
public class JsonMessageBodyWriter implements MessageBodyWriter<Object> {

    @Override
    public boolean isWriteable(
            final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
                || mediaType.isCompatible(MediaType.valueOf("application/*+json"));
    }

    @Override
    public void writeTo(
            final Object o,
            final Class<?> type,
            final Type genericType,
            final Annotation[] annotations,
            final MediaType mediaType,
            final MultivaluedMap<String, Object> httpHeaders,
            final OutputStream entityStream) {
        try {
            JsonUtils.toStream(entityStream, o);
        } catch (final Exception exception) {
            throw new RuntimeException(
                    String.format("Error serializing JSON with Jackson: %s", exception.getMessage()), exception);
        }
    }
}

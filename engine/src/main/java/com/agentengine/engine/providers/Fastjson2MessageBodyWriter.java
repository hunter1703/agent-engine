package com.agentengine.engine.providers;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.exceptions.JsonSerializationException;
import io.quarkus.runtime.annotations.RegisterForReflection;
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
@RegisterForReflection
public class Fastjson2MessageBodyWriter implements MessageBodyWriter<Object> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE) ||
               mediaType.isCompatible(MediaType.valueOf("application/*+json"));
    }

    @Override
    public long getSize(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations,
                       MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                       OutputStream entityStream) {
        try {
            String jsonString = JsonUtils.toJson(o);
            entityStream.write(jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new JsonSerializationException(STR."Error serializing JSON with Fastjson2: \{e.getMessage()}", e);
        }
    }
}
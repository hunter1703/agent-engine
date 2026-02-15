package com.agentengine.engine.providers;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.exceptions.JsonDeserializationException;
import io.quarkus.runtime.annotations.RegisterForReflection;
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
@RegisterForReflection
public class Fastjson2MessageBodyReader implements MessageBodyReader<Object> {

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
        || mediaType.isCompatible(MediaType.valueOf("application/*+json"));
  }

  @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations,
                          MediaType mediaType, MultivaluedMap<String, String> httpHeaders,
                          InputStream entityStream) throws JsonDeserializationException {
        try {
            return JsonUtils.fromStream(entityStream, type);
        } catch (Exception e) {
            throw new JsonDeserializationException(STR."Error deserializing JSON with Fastjson2: \{e.getMessage()}", e);
        }
    }
}
package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.JsonUtils;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.EventType;
import com.agui.community.core.message.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
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

  /**
   * A copy of the shared base mapper with an AG-UI {@link Event} mixin registered. The mixin
   * injects the {@code "type"} field into every serialized {@link Event} — Jackson does not include
   * interface methods that are not record components, so without this the {@code type()}
   * discriminator is silently omitted from the wire JSON.
   */
  private static final ObjectMapper MAPPER =
      JsonUtils.copyMapper()
          .registerModule(
              new SimpleModule("AGUIEventModule")
                  .setMixInAnnotation(Event.class, EventTypeMixin.class)
                  .addSerializer(Role.class, new RoleSerializer()));

  @Override
  public boolean isWriteable(
      final Class<?> type,
      final Type genericType,
      final Annotation[] annotations,
      final MediaType mediaType) {
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
      MAPPER.writeValue(entityStream, o);
    } catch (final Exception exception) {
      throw new RuntimeException(
          String.format("Error serializing JSON with Jackson: %s", exception.getMessage()),
          exception);
    }
  }

  /**
   * Mixin that annotates {@link Event#type()} so Jackson serializes it as the {@code "type"}
   * property.
   */
  private abstract static class EventTypeMixin {
    @JsonProperty("type")
    public abstract EventType type();
  }

  /**
   * Serializes {@link Role} as its lowercase wire value (e.g. {@code "user"}, {@code "assistant"}).
   */
  private static final class RoleSerializer extends StdSerializer<Role> {
    private RoleSerializer() {
      super(Role.class);
    }

    @Override
    public void serialize(
        final Role value, final JsonGenerator gen, final SerializerProvider provider)
        throws java.io.IOException {
      gen.writeString(value.value());
    }
  }
}

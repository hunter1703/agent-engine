package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.JsonUtils;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.message.*;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.UUID;

@Provider
@Consumes({MediaType.APPLICATION_JSON, "application/*+json"})
public class JsonMessageBodyReader implements MessageBodyReader<Object> {

  /**
   * A copy of the shared base mapper with AG-UI-specific deserializer mixins registered. Kept here
   * so this config stays in the REST module and does not leak into the common utility mapper.
   */
  private static final ObjectMapper MAPPER =
      JsonUtils.copyMapper()
          .registerModule(
              new SimpleModule("AGUIModule").setMixInAnnotation(Message.class, MessageMixin.class));

  @Override
  public boolean isReadable(
      final Class<?> type,
      final Type genericType,
      final Annotation[] annotations,
      final MediaType mediaType) {
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
      if (RunAgentInput.class.equals(type)) {
        // Default threadId/runId to empty string when absent so the record's
        // requireNonNull check does not fire. RuntimeServiceImpl already treats
        // a blank threadId as "create a new session".
        final ObjectNode node = (ObjectNode) MAPPER.readTree(entityStream);
        if (!node.hasNonNull("threadId")) {
          node.set("threadId", TextNode.valueOf(""));
        }
        if (!node.hasNonNull("runId")) {
          node.set("runId", TextNode.valueOf(""));
        }
        // Default missing message id to a generated UUID so the concrete Message
        // record's requireNonNull check does not fire on partial payloads.
        if (node.hasNonNull("messages") && node.get("messages").isArray()) {
          for (final JsonNode msg : node.get("messages")) {
            if (msg.isObject() && !msg.hasNonNull("id")) {
              ((ObjectNode) msg).set("id", TextNode.valueOf(UUID.randomUUID().toString()));
            }
          }
        }
        return MAPPER.treeToValue(node, RunAgentInput.class);
      }
      return MAPPER.readValue(entityStream, type);
    } catch (final Exception exception) {
      throw new IllegalArgumentException(
          String.format("Error deserializing JSON with Jackson: %s", exception.getMessage()),
          exception);
    }
  }

  /**
   * Mixin that configures polymorphic deserialization for {@link Message} based on the {@code
   * "role"} property.
   */
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = "role",
      visible = true)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
    @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
    @JsonSubTypes.Type(value = SystemMessage.class, name = "system"),
    @JsonSubTypes.Type(value = DeveloperMessage.class, name = "developer"),
    @JsonSubTypes.Type(value = ToolMessage.class, name = "tool")
  })
  private abstract static class MessageMixin {}
}

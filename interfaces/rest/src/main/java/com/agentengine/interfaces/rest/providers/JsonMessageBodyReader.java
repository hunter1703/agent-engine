package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.JsonUtils;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.message.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Consumes({MediaType.APPLICATION_JSON, "application/*+json"})
public class JsonMessageBodyReader implements MessageBodyReader<Object> {

    /**
     * A copy of the shared base mapper with AG-UI-specific deserializers registered.
     * Kept here so this config stays in the REST module and does not leak into the
     * common utility mapper.
     */
    private static final ObjectMapper MAPPER = JsonUtils.copyMapper()
            .registerModule(
                    new SimpleModule("AGUIModule").addDeserializer(Message.class, new AGUIMessageDeserializer()));

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
                return MAPPER.treeToValue(node, RunAgentInput.class);
            }
            return MAPPER.readValue(entityStream, type);
        } catch (final Exception exception) {
            throw new IllegalArgumentException(
                    String.format("Error deserializing JSON with Jackson: %s", exception.getMessage()), exception);
        }
    }

    /**
     * Deserializes the AG-UI {@link Message} sealed interface by reading the
     * {@code "role"} discriminator and delegating to the correct concrete type.
     */
    private static final class AGUIMessageDeserializer extends StdDeserializer<Message> {

        private AGUIMessageDeserializer() {
            super(Message.class);
        }

        @Override
        public Message deserialize(final JsonParser p, final DeserializationContext ctx) throws IOException {
            final ObjectNode node = p.readValueAsTree();
            final Role role = Role.fromValue(node.path("role").asText(""));
            final ObjectMapper mapper = (ObjectMapper) p.getCodec();
            return switch (role) {
                case Role.USER -> mapper.treeToValue(node, UserMessage.class);
                case Role.ASSISTANT -> mapper.treeToValue(node, AssistantMessage.class);
                case Role.SYSTEM -> mapper.treeToValue(node, SystemMessage.class);
                case Role.DEVELOPER -> mapper.treeToValue(node, DeveloperMessage.class);
                case Role.TOOL -> mapper.treeToValue(node, ToolMessage.class);
            };
        }
    }
}

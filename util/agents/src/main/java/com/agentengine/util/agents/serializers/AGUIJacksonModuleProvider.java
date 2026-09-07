package com.agentengine.util.agents.serializers;

import com.agentengine.util.common.CodecModuleProvider;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.ActivityDeltaEvent;
import com.agui.community.core.event.ActivitySnapshotEvent;
import com.agui.community.core.event.CustomEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.EventType;
import com.agui.community.core.event.MessagesSnapshotEvent;
import com.agui.community.core.event.MetaEvent;
import com.agui.community.core.event.RawEvent;
import com.agui.community.core.event.ReasoningEncryptedValueEvent;
import com.agui.community.core.event.ReasoningEndEvent;
import com.agui.community.core.event.ReasoningMessageChunkEvent;
import com.agui.community.core.event.ReasoningMessageContentEvent;
import com.agui.community.core.event.ReasoningMessageEndEvent;
import com.agui.community.core.event.ReasoningMessageStartEvent;
import com.agui.community.core.event.ReasoningStartEvent;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.RunStartedEvent;
import com.agui.community.core.event.StateDeltaEvent;
import com.agui.community.core.event.StateSnapshotEvent;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.StepStartedEvent;
import com.agui.community.core.event.TextMessageChunkEvent;
import com.agui.community.core.event.TextMessageContentEvent;
import com.agui.community.core.event.TextMessageEndEvent;
import com.agui.community.core.event.TextMessageStartEvent;
import com.agui.community.core.event.ToolCallArgsEvent;
import com.agui.community.core.event.ToolCallChunkEvent;
import com.agui.community.core.event.ToolCallEndEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.event.ToolCallStartEvent;
import com.agui.community.core.message.AssistantMessage;
import com.agui.community.core.message.DeveloperMessage;
import com.agui.community.core.message.Message;
import com.agui.community.core.message.Role;
import com.agui.community.core.message.SystemMessage;
import com.agui.community.core.message.ToolMessage;
import com.agui.community.core.message.UserMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.UUID;

@Singleton
public final class AGUIJacksonModuleProvider implements CodecModuleProvider {

  @Override
  public Module getModule() {
    return new AGUIJacksonModule();
  }

  private static final class AGUIJacksonModule extends SimpleModule {

    private AGUIJacksonModule() {
      super(AGUIJacksonModule.class.getSimpleName());
      setMixInAnnotation(Event.class, EventTypeMixin.class);
      addSerializer(Event.class, new AGUIEventCodec());
      addSerializer(Role.class, new RoleSerializer());
      addDeserializer(Role.class, new RoleDeserializer());
      setMixInAnnotation(Message.class, MessageMixin.class);
      setDeserializerModifier(
          new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(
                final DeserializationConfig config,
                final BeanDescription beanDesc,
                final JsonDeserializer<?> deserializer) {
              if (beanDesc.getBeanClass() == RunAgentInput.class) {
                return new RunAgentInputDeserializer(deserializer);
              }
              return deserializer;
            }
          });
    }

    /**
     * Annotates {@link Event#type()} as the {@code "type"} property, and uses it as the polymorphic
     * discriminator for deserializing a heterogeneous {@code Event} batch — the same {@code
     * EXISTING_PROPERTY} pattern as {@link MessageMixin} below, keyed off the discriminator every
     * {@code Event} already carries on the wire. This means a mixed-type batch never needs
     * Jackson's generic {@code @class} default typing to round-trip, which otherwise would tag
     * every non-final class reachable from the batch (including {@code rawEvent}'s raw {@code
     * HashMap}), not just the {@code Event} elements themselves.
     */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
    @JsonSubTypes({
      @JsonSubTypes.Type(value = RunStartedEvent.class, name = "RUN_STARTED"),
      @JsonSubTypes.Type(value = RunFinishedEvent.class, name = "RUN_FINISHED"),
      @JsonSubTypes.Type(value = RunErrorEvent.class, name = "RUN_ERROR"),
      @JsonSubTypes.Type(value = StepStartedEvent.class, name = "STEP_STARTED"),
      @JsonSubTypes.Type(value = StepFinishedEvent.class, name = "STEP_FINISHED"),
      @JsonSubTypes.Type(value = TextMessageStartEvent.class, name = "TEXT_MESSAGE_START"),
      @JsonSubTypes.Type(value = TextMessageContentEvent.class, name = "TEXT_MESSAGE_CONTENT"),
      @JsonSubTypes.Type(value = TextMessageEndEvent.class, name = "TEXT_MESSAGE_END"),
      @JsonSubTypes.Type(value = TextMessageChunkEvent.class, name = "TEXT_MESSAGE_CHUNK"),
      @JsonSubTypes.Type(value = ToolCallStartEvent.class, name = "TOOL_CALL_START"),
      @JsonSubTypes.Type(value = ToolCallArgsEvent.class, name = "TOOL_CALL_ARGS"),
      @JsonSubTypes.Type(value = ToolCallEndEvent.class, name = "TOOL_CALL_END"),
      @JsonSubTypes.Type(value = ToolCallChunkEvent.class, name = "TOOL_CALL_CHUNK"),
      @JsonSubTypes.Type(value = ToolCallResultEvent.class, name = "TOOL_CALL_RESULT"),
      @JsonSubTypes.Type(value = ReasoningStartEvent.class, name = "REASONING_START"),
      @JsonSubTypes.Type(value = ReasoningEndEvent.class, name = "REASONING_END"),
      @JsonSubTypes.Type(
          value = ReasoningMessageStartEvent.class,
          name = "REASONING_MESSAGE_START"),
      @JsonSubTypes.Type(
          value = ReasoningMessageContentEvent.class,
          name = "REASONING_MESSAGE_CONTENT"),
      @JsonSubTypes.Type(value = ReasoningMessageEndEvent.class, name = "REASONING_MESSAGE_END"),
      @JsonSubTypes.Type(
          value = ReasoningMessageChunkEvent.class,
          name = "REASONING_MESSAGE_CHUNK"),
      @JsonSubTypes.Type(
          value = ReasoningEncryptedValueEvent.class,
          name = "REASONING_ENCRYPTED_VALUE"),
      @JsonSubTypes.Type(value = StateSnapshotEvent.class, name = "STATE_SNAPSHOT"),
      @JsonSubTypes.Type(value = StateDeltaEvent.class, name = "STATE_DELTA"),
      @JsonSubTypes.Type(value = MessagesSnapshotEvent.class, name = "MESSAGES_SNAPSHOT"),
      @JsonSubTypes.Type(value = ActivitySnapshotEvent.class, name = "ACTIVITY_SNAPSHOT"),
      @JsonSubTypes.Type(value = ActivityDeltaEvent.class, name = "ACTIVITY_DELTA"),
      @JsonSubTypes.Type(value = RawEvent.class, name = "RAW"),
      @JsonSubTypes.Type(value = CustomEvent.class, name = "CUSTOM"),
      @JsonSubTypes.Type(value = MetaEvent.class, name = "META_EVENT")
    })
    private abstract static class EventTypeMixin {
      @JsonProperty("type")
      public abstract EventType type();
    }

    /**
     * Serializes {@link Role} as its lowercase wire value (e.g. {@code "user"}, {@code
     * "assistant"}).
     */
    private static final class RoleSerializer extends StdSerializer<Role> {
      private RoleSerializer() {
        super(Role.class);
      }

      @Override
      public void serialize(
          final Role value, final JsonGenerator gen, final SerializerProvider provider)
          throws IOException {
        gen.writeString(value.value());
      }
    }

    /**
     * Reads {@link Role} back from its lowercase wire value — the inverse of {@link
     * RoleSerializer}. Without this, Jackson's default enum deserializer only accepts the
     * constant's own Java name ({@code "ASSISTANT"}), not the lowercase value {@link
     * RoleSerializer} actually writes.
     */
    private static final class RoleDeserializer extends StdDeserializer<Role> {
      private RoleDeserializer() {
        super(Role.class);
      }

      @Override
      public Role deserialize(final JsonParser parser, final DeserializationContext context)
          throws IOException {
        return Role.fromValue(parser.getValueAsString());
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

    /**
     * Patches the incoming JSON tree, then delegates to {@link RunAgentInput}'s default (record)
     * deserializer to do the actual binding, rather than duplicating its field list here (which
     * would drift out of sync with the library's own shape over time) or letting its {@code
     * requireNonNull} constructor checks reject an otherwise-valid partial payload. The AG-UI
     * protocol allows a client to omit {@code threadId}/{@code runId}/message {@code id} when
     * starting a new run — the server is expected to allocate them — but {@code RunAgentInput}
     * (from {@code com.ag-ui.community:java-core}, not our code) requires them non-null at
     * construction. {@code RuntimeServiceImpl} already treats a blank {@code threadId} as "create a
     * new session", so defaulting to {@code ""} here matches an existing convention rather than
     * inventing one.
     *
     * <p>Extends {@link DelegatingDeserializer} rather than implementing {@code
     * ResolvableDeserializer} by hand — it already forwards {@code resolve()} to the wrapped
     * deserializer, which the record's default deserializer genuinely needs (skipping it breaks
     * construction entirely, not just an edge case).
     */
    private static final class RunAgentInputDeserializer extends DelegatingDeserializer {

      private RunAgentInputDeserializer(final JsonDeserializer<?> delegatee) {
        super(delegatee);
      }

      @Override
      protected JsonDeserializer<?> newDelegatingInstance(final JsonDeserializer<?> newDelegatee) {
        return new RunAgentInputDeserializer(newDelegatee);
      }

      @Override
      public Object deserialize(final JsonParser parser, final DeserializationContext context)
          throws IOException {
        final ObjectCodec codec = parser.getCodec();
        final ObjectNode node = codec.readTree(parser);
        if (!node.hasNonNull("threadId")) {
          node.put("threadId", "");
        }
        if (!node.hasNonNull("runId")) {
          node.put("runId", "");
        }
        if (node.hasNonNull("messages") && node.get("messages").isArray()) {
          for (final JsonNode message : node.get("messages")) {
            if (message.isObject() && !message.hasNonNull("id")) {
              ((ObjectNode) message).put("id", UUID.randomUUID().toString());
            }
          }
        }
        final JsonParser patchedParser = codec.treeAsTokens(node);
        patchedParser.nextToken();
        return getDelegatee().deserialize(patchedParser, context);
      }
    }
  }
}

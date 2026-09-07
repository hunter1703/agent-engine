package com.agentengine.util.agents.serializers;

import com.agentengine.util.common.JsonUtils;
import com.agui.community.core.event.CustomEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.ReasoningEndEvent;
import com.agui.community.core.event.ReasoningMessageChunkEvent;
import com.agui.community.core.event.ReasoningMessageEndEvent;
import com.agui.community.core.event.ReasoningMessageStartEvent;
import com.agui.community.core.event.ReasoningStartEvent;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.RunStartedEvent;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.StepStartedEvent;
import com.agui.community.core.event.TextMessageChunkEvent;
import com.agui.community.core.event.TextMessageEndEvent;
import com.agui.community.core.event.TextMessageStartEvent;
import com.agui.community.core.event.ToolCallArgsEvent;
import com.agui.community.core.event.ToolCallEndEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.event.ToolCallStartEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import java.io.IOException;

/**
 * Hand-written {@link JsonSerializer} for {@link Event}, same trade-off as {@link
 * com.agentengine.util.agents.beans.SessionEventCodec}: {@code RuntimeService#startSessionAgui}/
 * {@code #subscribeToSessionAgui} are hot enough to skip Jackson's automatic per-property
 * machinery.
 *
 * <p>It's also a correctness fix, not just perf: Jackson decides whether to tag an {@code
 * Object}-declared property with default-typing's {@code @class} based on its *declared* type,
 * once, before seeing the value — so no annotation on the runtime class (e.g. {@code HashMap}) can
 * stop it. Hand-writing each field bypasses that per-property decision. {@code rawEvent} goes
 * further: it's written through a dedicated, always-typing-free {@link ObjectMapper} rather than
 * {@link SerializerProvider#defaultSerializeValue}, so it stays clean regardless of any
 * default-typing policy configured elsewhere.
 *
 * <p>{@link #serializeWithType} calls {@link #serialize} directly, ignoring the supplied {@link
 * TypeSerializer}, so {@code Event} itself doesn't get {@code @class}-wrapped one level up.
 *
 * <p>Deserialization isn't handled here — it's not hot (raw-passthrough never rebuilds real {@code
 * Event} objects) and still round-trips fine via {@link AGUIJacksonModuleProvider}'s {@code
 * EventTypeMixin}. TODO: add a matching {@code JsonDeserializer<Event>} here if that changes.
 */
public final class AGUIEventCodec extends JsonSerializer<Event> {

  // Field names as wire constants rather than repeated literals — Event's own subtypes are a
  // third-party library (com.ag-ui.community:java-core), not ours, so unlike SessionEventCodec's
  // FIELD_* constants (owned by SessionEvent itself) these live here instead.
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_THREAD_ID = "threadId";
  private static final String FIELD_RUN_ID = "runId";
  private static final String FIELD_PARENT_RUN_ID = "parentRunId";
  private static final String FIELD_INPUT = "input";
  private static final String FIELD_TIMESTAMP = "timestamp";
  private static final String FIELD_RAW_EVENT = "rawEvent";
  private static final String FIELD_OUTCOME = "outcome";
  private static final String FIELD_RESULT = "result";
  private static final String FIELD_MESSAGE = "message";
  private static final String FIELD_CODE = "code";
  private static final String FIELD_STEP_NAME = "stepName";
  private static final String FIELD_MESSAGE_ID = "messageId";
  private static final String FIELD_ROLE = "role";
  private static final String FIELD_DELTA = "delta";
  private static final String FIELD_TOOL_CALL_ID = "toolCallId";
  private static final String FIELD_TOOL_CALL_NAME = "toolCallName";
  private static final String FIELD_PARENT_MESSAGE_ID = "parentMessageId";
  private static final String FIELD_CONTENT = "content";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_VALUE = "value";
  private static final ObjectMapper DEFAULT_MAPPER = JsonUtils.copyMapper();

  @Override
  public void serialize(
      final Event value, final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();
    gen.writeStringField(FIELD_TYPE, value.type().name());
    switch (value) {
      case RunStartedEvent event -> {
        writeString(gen, FIELD_THREAD_ID, event.threadId());
        writeString(gen, FIELD_RUN_ID, event.runId());
        writeString(gen, FIELD_PARENT_RUN_ID, event.parentRunId());
        writeMap(gen, FIELD_INPUT, event.input());
      }
      case RunFinishedEvent event -> {
        writeString(gen, FIELD_THREAD_ID, event.threadId());
        writeString(gen, FIELD_RUN_ID, event.runId());
        writeMap(gen, FIELD_OUTCOME, event.outcome());
        writeMap(gen, FIELD_RESULT, event.result());
      }
      case RunErrorEvent event -> {
        writeString(gen, FIELD_MESSAGE, event.message());
        writeString(gen, FIELD_CODE, event.code());
      }
      case StepStartedEvent event -> {
        writeString(gen, FIELD_STEP_NAME, event.stepName());
      }
      case StepFinishedEvent event -> {
        writeString(gen, FIELD_STEP_NAME, event.stepName());
      }
      case TextMessageStartEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
        writeObject(gen, serializers, FIELD_ROLE, event.role());
      }
      case TextMessageEndEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
      }
      case TextMessageChunkEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
        writeObject(gen, serializers, FIELD_ROLE, event.role());
        writeString(gen, FIELD_DELTA, event.delta());
      }
      case ToolCallStartEvent event -> {
        writeString(gen, FIELD_TOOL_CALL_ID, event.toolCallId());
        writeString(gen, FIELD_TOOL_CALL_NAME, event.toolCallName());
        writeString(gen, FIELD_PARENT_MESSAGE_ID, event.parentMessageId());
      }
      case ToolCallArgsEvent event -> {
        writeString(gen, FIELD_TOOL_CALL_ID, event.toolCallId());
        writeString(gen, FIELD_DELTA, event.delta());
      }
      case ToolCallEndEvent event -> {
        writeString(gen, FIELD_TOOL_CALL_ID, event.toolCallId());
      }
      case ToolCallResultEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
        writeString(gen, FIELD_TOOL_CALL_ID, event.toolCallId());
        writeString(gen, FIELD_CONTENT, event.content());
        writeObject(gen, serializers, FIELD_ROLE, event.role());
      }
      case ReasoningStartEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
      }
      case ReasoningEndEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
      }
      case ReasoningMessageStartEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
      }
      case ReasoningMessageChunkEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
        writeString(gen, FIELD_DELTA, event.delta());
      }
      case ReasoningMessageEndEvent event -> {
        writeString(gen, FIELD_MESSAGE_ID, event.messageId());
      }
      case CustomEvent event -> {
        writeString(gen, FIELD_NAME, event.name());
        writeMap(gen, FIELD_VALUE, event.value());
      }
      // Not handling everything right now
      default ->
          throw new IllegalArgumentException(
              "Unsupported Event subtype: " + value.getClass().getName());
    }
    // timestamp/rawEvent are declared on Event itself, common to every subtype, so they're written
    // once here off value directly rather than repeated per case above.
    writeTrailer(gen, value.timestamp(), value.rawEvent());
    gen.writeEndObject();
  }

  // Default typing (activated for the whole batch, since it's heterogeneous) would otherwise wrap
  // this class's own output with @class at the Event level too — ignoring typeSer here keeps the
  // discriminator this class already writes itself ("type") as the only one on the wire.
  @Override
  public void serializeWithType(
      final Event value,
      final JsonGenerator gen,
      final SerializerProvider serializers,
      final TypeSerializer typeSer)
      throws IOException {
    serialize(value, gen, serializers);
  }

  private static void writeTrailer(
      final JsonGenerator gen, final Long timestamp, final Object rawEvent) throws IOException {
    writeNumber(gen, FIELD_TIMESTAMP, timestamp);
    writeMap(gen, FIELD_RAW_EVENT, rawEvent);
  }

  private static void writeString(final JsonGenerator gen, final String name, final String value)
      throws IOException {
    if (value != null) {
      gen.writeStringField(name, value);
    }
  }

  private static void writeNumber(final JsonGenerator gen, final String name, final Long value)
      throws IOException {
    if (value != null) {
      gen.writeNumberField(name, value);
    }
  }

  private static void writeObject(
      final JsonGenerator gen,
      final SerializerProvider serializers,
      final String name,
      final Object value)
      throws IOException {
    if (value != null) {
      gen.writeFieldName(name);
      serializers.defaultSerializeValue(value, gen);
    }
  }

  private static void writeMap(final JsonGenerator gen, final String name, final Object value)
      throws IOException {
    if (value != null) {
      gen.writeFieldName(name);
      DEFAULT_MAPPER.writeValue(gen, value);
    }
  }
}

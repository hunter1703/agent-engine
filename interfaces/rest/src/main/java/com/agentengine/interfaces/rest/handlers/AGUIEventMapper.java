package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.ExceptionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.CustomEvent;
import com.agui.core.event.RunErrorEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.StepFinishedEvent;
import com.agui.core.event.StepStartedEvent;
import com.agui.core.event.TextMessageChunkEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.TextMessageEndEvent;
import com.agui.core.event.TextMessageStartEvent;
import com.agui.core.event.ThinkingEndEvent;
import com.agui.core.event.ThinkingStartEvent;
import com.agui.core.event.ToolCallArgsEvent;
import com.agui.core.event.ToolCallEndEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.event.ToolCallStartEvent;
import com.agui.core.message.Role;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps ADK events to AGUI events
 */
public final class AGUIEventMapper implements EventMapper<Event, BaseEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);
  private final MapperState state;

  public AGUIEventMapper(final String sessionId, final String agentId) {
    this.state = new MapperState(sessionId, agentId);
  }

  @Override
  public Flowable<BaseEvent> map(final Event event) {
    LOG.debug("Input event received for mapping - event={}", JsonUtils.toJson(event));

    if (state.runId == null && event.invocationId() != null) {
      state.runId = event.invocationId();
      final RunStartedEvent startEvent = new RunStartedEvent();
      startEvent.setRunId(state.runId);
      startEvent.setThreadId(state.sessionId);
      final BaseEvent decoratedStartEvent = decorateEvent(startEvent);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedStartEvent));

      return Flowable.concat(Flowable.just(decoratedStartEvent), mapEventInternal(event));
    }

    return mapEventInternal(event);
  }

  public Flowable<BaseEvent> onComplete() {
    final BaseEvent decoratedEvent = decorateEvent(buildRunFinished(state.runId));
    LOG.debug("Generated output event in onComplete - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  public Flowable<BaseEvent> onError(final Throwable throwable) {
    LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
    final RunErrorEvent errorEvent = new RunErrorEvent();
    errorEvent.setError(ExceptionUtils.getErrorMessage(throwable));
    final BaseEvent decoratedEvent = decorateEvent(errorEvent);
    LOG.debug("Generated output event in onError - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapEventInternal(final Event event) {
    LOG.debug("Processing internal mapping for event - event={}", JsonUtils.toJson(event));

    final List<Flowable<BaseEvent>> flows = new ArrayList<>();
    flows.add(mapStepStart());

    final Content content = event.content().orElse(null);
    if (content != null) {
      final boolean partial = event.partial().orElse(false);
      final List<Part> parts = content.parts().orElse(List.of());

      for (Part part : parts) {
        final String text = part.text().orElse(null);
        if (text != null) {
          if (part.thought().orElse(false)) {
            flows.add(mapThinking(text, partial));
          } else {
            flows.add(mapMessage(text, partial));
          }
        }

        part.functionCall().ifPresent(call -> flows.add(mapToolCall(call, partial)));
        part.functionResponse().ifPresent(response -> flows.add(mapToolResponse(response)));
      }
    }

    updatePendingToolCalls(event);
    flows.add(mapStepFinish(event));

    return Flowable.concat(flows);
  }

  private Flowable<BaseEvent> mapStepStart() {
    if (hasStepStarted()) {
      LOG.debug("Step already started, skipping StepStartedEvent generation");
      return Flowable.empty();
    }
    state.currentStepName = STR."step-\{UUID.randomUUID()}";
    final StepStartedEvent stepEvent = new StepStartedEvent();
    stepEvent.setStepName(state.currentStepName);
    final BaseEvent decoratedStepEvent = decorateEvent(stepEvent);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedStepEvent));
    return Flowable.just(decoratedStepEvent);
  }

  private boolean hasStepStarted() {
    return StringUtils.isNotBlank(state.currentStepName);
  }

  private Flowable<BaseEvent> mapStepFinish(final Event event) {
    // Corner case: keep steps open while a tool call is pending or event is
    // partial.
    if (!hasStepStarted() || CollectionUtils.isNotEmpty(state.pendingToolCalls) || event.partial().orElse(false)) {
      LOG.debug(
          "Skipping StepFinishedEvent generation due to conditions - hasStepStarted={}, pendingToolCalls.size={}, partial={}",
          hasStepStarted(), state.pendingToolCalls.size(), event.partial().orElse(false));
      return Flowable.empty();
    }

    final Content content = event.content().orElse(null);
    if (content == null) {
      LOG.debug("Skipping StepFinishedEvent generation due to null content");
      return Flowable.empty();
    }

    final List<Part> parts = content.parts().orElse(List.of());
    final boolean hasToolCalls = parts.stream().anyMatch(part -> part.functionCall().isPresent());
    if (hasToolCalls) {
      // Corner case: a tool call event should not close the step that started it.
      LOG.debug("Skipping StepFinishedEvent generation due to tool calls present");
      return Flowable.empty();
    }
    final boolean hasToolResponses = parts.stream().anyMatch(part -> part.functionResponse().isPresent());
    // Corner case: only assistant-authored text (not user text) can close a step.
    final boolean hasAssistantText = !"user".equalsIgnoreCase(event.author()) && parts.stream()
        .anyMatch(part -> !part.thought().orElse(false) && StringUtils.isNotBlank(part.text().orElse(null)));
    final boolean stepFinished = hasToolResponses || hasAssistantText;
    if (!stepFinished) {
      LOG.debug("Skipping StepFinishedEvent generation due to no tool responses or assistant text");
      return Flowable.empty();
    }
    final StepFinishedEvent stepEvent = new StepFinishedEvent();
    stepEvent.setStepName(state.currentStepName);
    state.currentStepName = null;
    final BaseEvent decoratedStepEvent = decorateEvent(stepEvent);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedStepEvent));
    return Flowable.just(decoratedStepEvent);
  }

  private void updatePendingToolCalls(final Event event) {
    for (final FunctionCall call : CollectionUtils.nullSafeList(event.functionCalls())) {
      call.id().ifPresent(state.pendingToolCalls::add);
    }
    for (final FunctionResponse response : collectFunctionResponses(event)) {
      response.id().ifPresent(state.pendingToolCalls::remove);
    }
  }

  private List<FunctionResponse> collectFunctionResponses(final Event event) {
    final List<FunctionResponse> functionResponses = CollectionUtils.nullSafeMutableList(event.functionResponses());
    final Content content = event.content().orElse(null);
    if (content != null) {
      for (final Part part : content.parts().orElse(List.of())) {
        part.functionResponse().ifPresent(functionResponses::add);
      }
    }
    return functionResponses;
  }

  private Flowable<BaseEvent> mapThinking(final String text, final boolean partial) {
    if (StringUtils.isBlank(text)) {
      LOG.debug("Skipping thinking mapping due to blank text - text='{}'", text);
      return Flowable.empty();
    }

    LOG.debug("Processing thinking mapping - text='{}', partial={}", text, partial);

    final CustomEvent thinkDelta = new CustomEvent();
    thinkDelta.setRawEvent(Map.of("name", "THINK_DELTA", "value", text));
    final BaseEvent decoratedThinkDelta = decorateEvent(thinkDelta);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedThinkDelta));

    return Flowable.concatArray(mapThinkingStart(), Flowable.just(decoratedThinkDelta), mapThinkingEnd(partial));
  }

  private Flowable<BaseEvent> mapThinkingStart() {
    if (state.isThinking) {
      LOG.debug("Already in thinking state, skipping ThinkingStartEvent generation");
      return Flowable.empty();
    }
    state.isThinking = true;
    final BaseEvent decoratedEvent = decorateEvent(new ThinkingStartEvent());
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapThinkingEnd(final boolean partial) {
    if (partial || !state.isThinking) {
      LOG.debug("Skipping ThinkingEndEvent generation - partial={}, isThinking={}", partial, state.isThinking);
      return Flowable.empty();
    }
    state.isThinking = false;
    final BaseEvent decoratedEvent = decorateEvent(new ThinkingEndEvent());
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapMessage(final String text, final boolean partial) {
    if (StringUtils.isBlank(text)) {
      LOG.debug("Skipping message mapping due to blank text - text='{}'", text);
      return Flowable.empty();
    }

    LOG.debug("Processing message mapping - text='{}', partial={}", text, partial);

    final List<Flowable<BaseEvent>> flows = new ArrayList<>();
    if (state.currentTextMessageId == null) {
      state.currentTextMessageId = STR."msg-\{UUID.randomUUID().toString()}";
      final TextMessageStartEvent start = new TextMessageStartEvent();
      start.setMessageId(state.currentTextMessageId);
      final BaseEvent decoratedStart = decorateEvent(start);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedStart));
      flows.add(Flowable.just(decoratedStart));
    }

    if (partial) {
      final TextMessageChunkEvent chunk = new TextMessageChunkEvent();
      chunk.setMessageId(state.currentTextMessageId);
      chunk.setDelta(text);
      final BaseEvent decoratedChunk = decorateEvent(chunk);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedChunk));
      flows.add(Flowable.just(decoratedChunk));
    } else {
      final TextMessageContentEvent content = new TextMessageContentEvent();
      content.setMessageId(state.currentTextMessageId);
      content.setDelta(text);
      final BaseEvent decoratedContent = decorateEvent(content);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedContent));
      flows.add(Flowable.just(decoratedContent));

      final TextMessageEndEvent end = new TextMessageEndEvent();
      end.setMessageId(state.currentTextMessageId);
      final BaseEvent decoratedEnd = decorateEvent(end);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedEnd));
      flows.add(Flowable.just(decoratedEnd));
      state.finalAnswer = text;
      state.currentTextMessageId = null;
    }

    return Flowable.concat(flows);
  }

  private Flowable<BaseEvent> mapToolCall(final FunctionCall call, final boolean partial) {
    final String callId = call.id().orElse(UUID.randomUUID().toString());
    final String toolName = call.name().orElse("unknown");
    final Map<String, Object> args = call.args().orElse(Map.of());

    LOG.debug("Processing tool call mapping - callId='{}', toolName='{}', partial={}, args={}", callId, toolName,
        partial, JsonUtils.toJson(args));

    final List<Flowable<BaseEvent>> flows = new ArrayList<>();

    if (!state.activeToolCalls.contains(callId)) {
      state.activeToolCalls.add(callId);
      final ToolCallStartEvent start = new ToolCallStartEvent();
      start.setToolCallId(callId);
      start.setToolCallName(toolName);
      final BaseEvent decoratedStart = decorateEvent(start);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedStart));
      flows.add(Flowable.just(decoratedStart));

      // If not partial, we should also send args now
      if (!partial) {
        final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
        argsEvent.setToolCallId(callId);
        argsEvent.setDelta(JsonUtils.toJson(args));
        final BaseEvent decoratedArgs = decorateEvent(argsEvent);
        LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedArgs));
        flows.add(Flowable.just(decoratedArgs));
      }
    }

    if (partial) {
      final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
      argsEvent.setToolCallId(callId);
      argsEvent.setDelta(JsonUtils.toJson(args));
      final BaseEvent decoratedArgs = decorateEvent(argsEvent);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedArgs));
      flows.add(Flowable.just(decoratedArgs));
    } else {
      final ToolCallEndEvent end = new ToolCallEndEvent();
      end.setToolCallId(callId);
      final BaseEvent decoratedEnd = decorateEvent(end);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedEnd));
      flows.add(Flowable.just(decoratedEnd));
      state.activeToolCalls.remove(callId);
    }

    return Flowable.concat(flows);
  }

  private Flowable<BaseEvent> mapToolResponse(final FunctionResponse response) {
    final String callId = response.id().orElse(UUID.randomUUID().toString());
    final String contentResult = JsonUtils.toJson(response.response().orElse(Map.of()));

    LOG.debug("Processing tool response mapping - callId='{}', contentResult='{}'", callId, contentResult);

    final ToolCallResultEvent result = new ToolCallResultEvent();
    result.setToolCallId(callId);
    result.setContent(contentResult);
    result.setRole(Role.tool);

    final BaseEvent decoratedResult = decorateEvent(result);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedResult));
    return Flowable.just(decoratedResult);
  }

  private RunFinishedEvent buildRunFinished(final String runId) {
    final RunFinishedEvent event = new RunFinishedEvent();
    event.setThreadId(state.sessionId);
    event.setRunId(runId);
    if (state.finalAnswer != null) {
      event.setResult(state.finalAnswer);
    }
    LOG.debug("Building RunFinishedEvent - runId='{}', sessionId='{}', finalAnswer='{}'", runId, state.sessionId,
        state.finalAnswer);
    return event;
  }

  private BaseEvent decorateEvent(final BaseEvent event) {
    event.setTimestamp(System.currentTimeMillis());
    final Map<String, Object> eventMap = JsonUtils.toMap(event);
    final Map<String, Object> rawEvent = CollectionUtils.nullSafeMutableMap(eventMap);
    rawEvent.put("agentId", state.agentId);
    event.setRawEvent(rawEvent);
    LOG.debug("Decorated event - eventType='{}', eventId='{}', agentId='{}'", event.getClass().getSimpleName(),
        getEventId(event), state.agentId);
    return event;
  }

  private String getEventId(BaseEvent event) {
    if (event instanceof RunStartedEvent runEvent) {
      return runEvent.getRunId();
    } else if (event instanceof RunFinishedEvent runEvent) {
      return runEvent.getRunId();
    } else if (event instanceof StepStartedEvent stepEvent) {
      return stepEvent.getStepName();
    } else if (event instanceof StepFinishedEvent stepEvent) {
      return stepEvent.getStepName();
    } else if (event instanceof TextMessageStartEvent msgEvent) {
      return msgEvent.getMessageId();
    } else if (event instanceof TextMessageChunkEvent msgEvent) {
      return msgEvent.getMessageId();
    } else if (event instanceof TextMessageContentEvent msgEvent) {
      return msgEvent.getMessageId();
    } else if (event instanceof TextMessageEndEvent msgEvent) {
      return msgEvent.getMessageId();
    } else if (event instanceof ToolCallStartEvent toolEvent) {
      return toolEvent.getToolCallId();
    } else if (event instanceof ToolCallArgsEvent toolEvent) {
      return toolEvent.getToolCallId();
    } else if (event instanceof ToolCallEndEvent toolEvent) {
      return toolEvent.getToolCallId();
    } else if (event instanceof ToolCallResultEvent toolEvent) {
      return toolEvent.getToolCallId();
    } else {
      return "unknown";
    }
  }

  private static final class MapperState {
    private final String sessionId;
    private final String agentId;
    private String runId;
    private String currentStepName;
    private String currentTextMessageId;
    private String finalAnswer;
    private boolean isThinking;
    private final Set<String> pendingToolCalls = new HashSet<>();
    private final List<String> activeToolCalls = new ArrayList<>();

    private MapperState(final String sessionId, final String agentId) {
      this.sessionId = sessionId;
      this.agentId = agentId;
    }
  }
}

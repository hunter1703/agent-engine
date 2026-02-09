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
    LOG.debug("Mapping event - event={}", JsonUtils.toJson(event));

    if (state.runId == null && event.invocationId() != null) {
      state.runId = event.invocationId();
      final RunStartedEvent startEvent = new RunStartedEvent();
      startEvent.setRunId(state.runId);
      startEvent.setThreadId(state.sessionId);
      return Flowable.concat(Flowable.just(decorateEvent(startEvent)), mapEventInternal(event));
    }

    return mapEventInternal(event);
  }

  public Flowable<BaseEvent> onComplete() {
    return Flowable.just(decorateEvent(buildRunFinished(state.runId)));
  }

  public Flowable<BaseEvent> onError(final Throwable throwable) {
    final RunErrorEvent errorEvent = new RunErrorEvent();
    errorEvent.setError(ExceptionUtils.getErrorMessage(throwable));
    return Flowable.just(decorateEvent(errorEvent));
  }

  private Flowable<BaseEvent> mapEventInternal(final Event event) {
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
      return Flowable.empty();
    }
    state.currentStepName = STR."step-\{UUID.randomUUID()}";
    final StepStartedEvent stepEvent = new StepStartedEvent();
    stepEvent.setStepName(state.currentStepName);
    return Flowable.just(decorateEvent(stepEvent));
  }

  private boolean hasStepStarted() {
    return StringUtils.isNotBlank(state.currentStepName);
  }

  private Flowable<BaseEvent> mapStepFinish(final Event event) {
    // Corner case: keep steps open while a tool call is pending or event is partial.
    if (!hasStepStarted() || CollectionUtils.isNotEmpty(state.pendingToolCalls) || event.partial().orElse(false)) {
      return Flowable.empty();
    }

    final Content content = event.content().orElse(null);
    if (content == null) {
      return Flowable.empty();
    }

    final List<Part> parts = content.parts().orElse(List.of());
    final boolean hasToolCalls = parts.stream().anyMatch(part -> part.functionCall().isPresent());
    if (hasToolCalls) {
      // Corner case: a tool call event should not close the step that started it.
      return Flowable.empty();
    }
    final boolean hasToolResponses = parts.stream().anyMatch(part -> part.functionResponse().isPresent());
    // Corner case: only assistant-authored text (not user text) can close a step.
    final boolean hasAssistantText = !"user".equalsIgnoreCase(event.author()) && parts.stream().anyMatch(part ->
        !part.thought().orElse(false) && StringUtils.isNotBlank(part.text().orElse(null)));
    final boolean stepFinished = hasToolResponses || hasAssistantText;
    if (!stepFinished) {
      return Flowable.empty();
    }
    final StepFinishedEvent stepEvent = new StepFinishedEvent();
    stepEvent.setStepName(state.currentStepName);
    state.currentStepName = null;
    return Flowable.just(decorateEvent(stepEvent));
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
      return Flowable.empty();
    }

    final CustomEvent thinkDelta = new CustomEvent();
    thinkDelta.setRawEvent(Map.of("name", "THINK_DELTA", "value", text));

    return Flowable.concatArray(
        mapThinkingStart(),
        Flowable.just(decorateEvent(thinkDelta)),
        mapThinkingEnd(partial));
  }

  private Flowable<BaseEvent> mapThinkingStart() {
    if (state.isThinking) {
      return Flowable.empty();
    }
    state.isThinking = true;
    return Flowable.just(decorateEvent(new ThinkingStartEvent()));
  }

  private Flowable<BaseEvent> mapThinkingEnd(final boolean partial) {
    if (partial || !state.isThinking) {
      return Flowable.empty();
    }
    state.isThinking = false;
    return Flowable.just(decorateEvent(new ThinkingEndEvent()));
  }

  private Flowable<BaseEvent> mapMessage(final String text, final boolean partial) {
    if (StringUtils.isBlank(text)) {
      return Flowable.empty();
    }

    final List<Flowable<BaseEvent>> flows = new ArrayList<>();
    if (state.currentTextMessageId == null) {
      state.currentTextMessageId = STR."msg-\{UUID.randomUUID().toString()}";
      final TextMessageStartEvent start = new TextMessageStartEvent();
      start.setMessageId(state.currentTextMessageId);
      flows.add(Flowable.just(decorateEvent(start)));
    }

    if (partial) {
      final TextMessageChunkEvent chunk = new TextMessageChunkEvent();
      chunk.setMessageId(state.currentTextMessageId);
      chunk.setDelta(text);
      flows.add(Flowable.just(decorateEvent(chunk)));
    } else {
      final TextMessageContentEvent content = new TextMessageContentEvent();
      content.setMessageId(state.currentTextMessageId);
      content.setDelta(text);
      flows.add(Flowable.just(decorateEvent(content)));

      final TextMessageEndEvent end = new TextMessageEndEvent();
      end.setMessageId(state.currentTextMessageId);
      flows.add(Flowable.just(decorateEvent(end)));
      state.finalAnswer = text;
      state.currentTextMessageId = null;
    }

    return Flowable.concat(flows);
  }

  private Flowable<BaseEvent> mapToolCall(final FunctionCall call, final boolean partial) {
    final String callId = call.id().orElse(UUID.randomUUID().toString());
    final String toolName = call.name().orElse("unknown");
    final Map<String, Object> args = call.args().orElse(Map.of());

    final List<Flowable<BaseEvent>> flows = new ArrayList<>();

    if (!state.activeToolCalls.contains(callId)) {
      state.activeToolCalls.add(callId);
      final ToolCallStartEvent start = new ToolCallStartEvent();
      start.setToolCallId(callId);
      start.setToolCallName(toolName);
      flows.add(Flowable.just(decorateEvent(start)));

      // If not partial, we should also send args now
      if (!partial) {
        final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
        argsEvent.setToolCallId(callId);
        argsEvent.setDelta(JsonUtils.toJson(args));
        flows.add(Flowable.just(decorateEvent(argsEvent)));
      }
    }

    if (partial) {
      final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
      argsEvent.setToolCallId(callId);
      argsEvent.setDelta(JsonUtils.toJson(args));
      flows.add(Flowable.just(decorateEvent(argsEvent)));
    } else {
      final ToolCallEndEvent end = new ToolCallEndEvent();
      end.setToolCallId(callId);
      flows.add(Flowable.just(decorateEvent(end)));
      state.activeToolCalls.remove(callId);
    }

    return Flowable.concat(flows);
  }

  private Flowable<BaseEvent> mapToolResponse(final FunctionResponse response) {
    final String callId = response.id().orElse(UUID.randomUUID().toString());
    final String contentResult = JsonUtils.toJson(response.response().orElse(Map.of()));

    final ToolCallResultEvent result = new ToolCallResultEvent();
    result.setToolCallId(callId);
    result.setContent(contentResult);
    result.setRole(Role.tool);

    return Flowable.just(decorateEvent(result));
  }

  private RunFinishedEvent buildRunFinished(final String runId) {
    final RunFinishedEvent event = new RunFinishedEvent();
    event.setThreadId(state.sessionId);
    event.setRunId(runId);
    if (state.finalAnswer != null) {
      event.setResult(state.finalAnswer);
    }
    return event;
  }

  private BaseEvent decorateEvent(final BaseEvent event) {
    event.setTimestamp(System.currentTimeMillis());
    final Map<String, Object> eventMap = JsonUtils.toMap(event);
    final Map<String, Object> rawEvent = CollectionUtils.nullSafeMutableMap(eventMap);
    rawEvent.put("agentId", state.agentId);
    event.setRawEvent(rawEvent);
    return event;
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

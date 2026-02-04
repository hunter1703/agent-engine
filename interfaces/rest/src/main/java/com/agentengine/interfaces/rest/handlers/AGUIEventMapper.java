package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.ExceptionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agui.core.event.BaseEvent;
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
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableTransformer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AGUIEventMapper implements EventMapper<Event, BaseEvent> {
  private static final Logger LOGGER = LoggerFactory.getLogger(AGUIEventMapper.class);
  private final EventState state;

  public AGUIEventMapper(final String threadId, final String agentId) {
    this.state = new EventState(threadId, agentId);
  }

  public Flowable<BaseEvent> map(final Event event) {
    if (isEmptyEvent(event)) {
      final String eventId = event != null ? event.id() : "unknown";
      LOGGER.info("Skipping empty event: {}", eventId);
      return Flowable.empty();
    }
    Functions.populateClientFunctionCallId(event);
    return Flowable.just(new EventContext(event, Flowable.empty())).compose(mapRunStartStage())
        .compose(mapStepStartStage()).compose(mapTextStage()).compose(mapFunctionCallsStage())
        .compose(mapFunctionResponsesStage()).compose(mapStepFinishStage()).concatMap(EventContext::mappedEvents);
  }

  public Flowable<BaseEvent> onComplete() {
    if (state.runId == null) {
      return Flowable.empty();
    }
    return Flowable.just(decorateEvent(buildRunFinished(state.runId)));
  }

  public Flowable<BaseEvent> onError(final Throwable throwable) {
    if (state.runId == null) {
      state.runId = "unknown";
    }
    final RunErrorEvent event = new RunErrorEvent();
    event.setError(ExceptionUtils.getErrorMessage(throwable));
    return Flowable.just(decorateEvent(event));
  }

  private FlowableTransformer<EventContext, EventContext> mapRunStartStage() {
    return upstream -> upstream.map(context -> context.append(mapRunStart(context.event())));
  }

  private FlowableTransformer<EventContext, EventContext> mapStepStartStage() {
    return upstream -> upstream.map(context -> context.append(mapStepStart()));
  }

  private FlowableTransformer<EventContext, EventContext> mapTextStage() {
    return upstream -> upstream.map(context -> context.append(mapText(context.event())));
  }

  private FlowableTransformer<EventContext, EventContext> mapFunctionCallsStage() {
    return upstream -> upstream.map(context -> context.append(mapFunctionCalls(context.event().functionCalls())));
  }

  private FlowableTransformer<EventContext, EventContext> mapFunctionResponsesStage() {
    return upstream -> upstream.map(context -> context.append(mapFunctionResponses(context.event())));
  }

  private FlowableTransformer<EventContext, EventContext> mapStepFinishStage() {
    return upstream -> upstream.map(context -> context.append(mapStepFinish(context.event())));
  }

  private Flowable<BaseEvent> mapRunStart(final Event event) {
    if (hasRunStarted()) {
      LOGGER.warn("A run is already started with id: {}", state.runId);
      return Flowable.empty();
    }
    state.runId = event.invocationId();
    final RunStartedEvent runStartedEvent = new RunStartedEvent();
    runStartedEvent.setThreadId(state.threadId);
    runStartedEvent.setRunId(state.runId);
    return Flowable.just(decorateEvent(runStartedEvent));
  }

  private boolean hasRunStarted() {
    return StringUtils.isNotBlank(state.runId);
  }

  private Flowable<BaseEvent> mapText(final Event event) {
    final Content content = event.content().orElse(null);
    if (content == null) {
      return Flowable.empty();
    }
    final boolean partial = event.partial().orElse(false);
    final List<Part> parts = content.parts().orElse(List.of());
    final StringBuilder answerBuilder = new StringBuilder();
    final StringBuilder thoughtBuilder = new StringBuilder();
    for (final Part part : parts) {
      final String text = part.text().orElse(null);
      if (StringUtils.isBlank(text)) {
        continue;
      }
      if (part.thought().orElse(false)) {
        thoughtBuilder.append(text);
      } else {
        answerBuilder.append(text);
      }
    }

    return Flowable.concatArray(mapThinking(thoughtBuilder.toString(), partial),
        mapAnswer(answerBuilder.toString(), partial));
  }

  private Flowable<BaseEvent> mapThinking(final String text, final boolean partial) {
    if (StringUtils.isBlank(text)) {
      return Flowable.empty();
    }
    return Flowable.concatArray(
        mapThinkingStart(),
        mapMessage(STR."think-\{UUID.randomUUID()}", text, partial),
        mapThinkingEnd(partial));
  }

  private Flowable<BaseEvent> mapThinkingStart() {
    if (state.thinkingStarted) {
      return Flowable.empty();
    }
    state.thinkingStarted = true;
    return Flowable.just(decorateEvent(new ThinkingStartEvent()));
  }

  private Flowable<BaseEvent> mapMessage(final String messageId, final String text, final boolean partial) {
    if (partial) {
      final TextMessageChunkEvent chunkEvent = new TextMessageChunkEvent();
      chunkEvent.setMessageId(messageId);
      chunkEvent.setDelta(text);
      chunkEvent.setRole("assistant");
      return Flowable.just(decorateEvent(chunkEvent));
    }
    final TextMessageContentEvent event = new TextMessageContentEvent();
    event.setMessageId(messageId);
    event.setDelta(text);
    return Flowable.just(decorateEvent(event));
  }

  private Flowable<BaseEvent> mapThinkingEnd(final boolean partial) {
    if (partial || !state.thinkingStarted) {
      return Flowable.empty();
    }
    state.thinkingStarted = false;
    return Flowable.just(decorateEvent(new ThinkingEndEvent()));
  }

  private Flowable<BaseEvent> mapAnswer(final String text, final boolean partial) {
    if (StringUtils.isNotBlank(text)) {
      if (!partial) {
        state.finalAnswer = text;
      }
      return Flowable.concatArray(mapAnswerStart(), mapMessage(state.currentTextMessageId, text, partial),
          mapAnswerEnd(partial));
    }
    if (!partial) {
      state.finalAnswer = text;
    }
    return Flowable.empty();
  }

  private Flowable<BaseEvent> mapAnswerStart() {
    if (hasAnswerStarted()) {
      return Flowable.empty();
    }
    state.currentTextMessageId = STR."msg-\{UUID.randomUUID()}";
    final TextMessageStartEvent event = new TextMessageStartEvent();
    event.setMessageId(state.currentTextMessageId);
    event.setRole("assistant");
    return Flowable.just(decorateEvent(event));
  }

  private boolean hasAnswerStarted() {
    return StringUtils.isNotBlank(state.currentTextMessageId);
  }

  private Flowable<BaseEvent> mapAnswerEnd(final boolean partial) {
    if (partial || !hasAnswerStarted()) {
      return Flowable.empty();
    }
    final TextMessageEndEvent event = new TextMessageEndEvent();
    event.setMessageId(state.currentTextMessageId);
    state.currentTextMessageId = null;
    return Flowable.just(decorateEvent(event));
  }

  private Flowable<BaseEvent> mapFunctionCalls(final List<FunctionCall> calls) {
    return Flowable.fromIterable(CollectionUtils.nullSafeList(calls)).concatMap(call -> {
      final String toolCallId = call.id().orElseThrow();
      final String name = call.name().orElse("tool");

      state.pendingToolCalls.add(toolCallId);

      return Flowable.concatArray(mapToolCallStart(toolCallId, name), mapToolCallArgs(call, toolCallId),
          mapToolCallEnd(toolCallId));
    });
  }

  private Flowable<BaseEvent> mapToolCallEnd(final String toolCallId) {
    final ToolCallEndEvent end = new ToolCallEndEvent();
    end.setToolCallId(toolCallId);
    return Flowable.just(decorateEvent(end));
  }

  private Flowable<BaseEvent> mapToolCallArgs(final FunctionCall call, final String toolCallId) {
    final Map<String, Object> args = call.args().orElse(Map.of());
    if (CollectionUtils.isEmpty(args)) {
      return Flowable.empty();
    }
    final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
    argsEvent.setToolCallId(toolCallId);
    argsEvent.setDelta(JsonUtils.toJson(args));
    return Flowable.just(decorateEvent(argsEvent));
  }

  private Flowable<BaseEvent> mapToolCallStart(final String toolCallId, final String toolCallName) {
    final ToolCallStartEvent start = new ToolCallStartEvent();
    start.setToolCallId(toolCallId);
    start.setToolCallName(toolCallName);
    return Flowable.just(decorateEvent(start));
  }

  private Flowable<BaseEvent> mapFunctionResponses(final Event event) {
    final Set<String> processedIds = new HashSet<>();
    return functionResponses(event).concatMap(response -> {
      final String toolCallId = response.id().orElseThrow();
      if (processedIds.contains(toolCallId)) {
        return Flowable.empty();
      }
      final Map<String, Object> payload = response.response().orElse(Map.of());
      state.pendingToolCalls.remove(toolCallId);
      processedIds.add(toolCallId);
      return Flowable.just(decorateEvent(buildToolResult(toolCallId, payload)));
    });
  }

  private Flowable<FunctionResponse> functionResponses(final Event event) {
    final List<FunctionResponse> functionResponses = CollectionUtils.nullSafeMutableList(event.functionResponses());
    final Content content = event.content().orElse(null);
    if (content != null) {
      for (final Part part : content.parts().orElse(List.of())) {
        part.functionResponse().ifPresent(functionResponses::add);
      }
    }
    return Flowable.fromIterable(functionResponses);
  }

  private ToolCallResultEvent buildToolResult(final String toolCallId, final Map<String, Object> payload) {
    final ToolCallResultEvent event = new ToolCallResultEvent();
    event.setToolCallId(toolCallId);
    event.setMessageId(toolCallId);
    event.setRole(Role.tool);
    event.setContent(JsonUtils.toJson(payload));
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
    if (!hasStepStarted() || CollectionUtils.isNotEmpty(state.pendingToolCalls) || event.partial().orElse(false)) {
      return Flowable.empty();
    }
    final Content content = event.content().orElse(null);
    boolean stepFinished = true;
    if (content != null) {
      stepFinished = content.parts().orElse(List.of()).stream().anyMatch(part -> !part.thought().orElse(false));
    }
    if (!stepFinished) {
      return Flowable.empty();
    }
    final StepFinishedEvent stepEvent = new StepFinishedEvent();
    stepEvent.setStepName(state.currentStepName);
    state.currentStepName = null;
    return Flowable.just(decorateEvent(stepEvent));
  }

  private RunFinishedEvent buildRunFinished(final String runId) {
    final RunFinishedEvent event = new RunFinishedEvent();
    event.setThreadId(state.threadId);
    event.setRunId(runId);
    if (state.finalAnswer != null) {
      event.setResult(state.finalAnswer);
    }
    return event;
  }

  private static boolean isEmptyEvent(final Event event) {
    if (event == null) {
      return true;
    }
    if (CollectionUtils.isNotEmpty(event.functionCalls())) {
      return false;
    }
    final Content content = event.content().orElse(null);
    if (content == null) {
      return true;
    }
    for (final Part part : content.parts().orElse(List.of())) {
      if (StringUtils.isNotBlank(part.text().orElse(null))) {
        return false;
      }
      if (part.functionResponse().isPresent()) {
        return false;
      }
    }
    return true;
  }

  private record EventContext(Event event, Flowable<BaseEvent> mappedEvents) {
    private EventContext append(final Flowable<BaseEvent> additional) {
      return new EventContext(event, mappedEvents.concatWith(additional));
    }
  }

  private static final class EventState {
    private final String threadId;
    private final String agentId;
    private String runId;
    private String currentStepName;
    private String currentTextMessageId;
    private String finalAnswer;
    private boolean thinkingStarted;
    private final Set<String> pendingToolCalls = new HashSet<>();

    private EventState(final String threadId, final String agentId) {
      this.threadId = threadId;
      this.agentId = agentId;
    }
  }
}

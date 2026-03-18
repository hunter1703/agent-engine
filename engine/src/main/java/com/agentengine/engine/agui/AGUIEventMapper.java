package com.agentengine.engine.agui;

import com.agentengine.engine.EventMapper;
import com.agentengine.engine.api.agui.CorrectionEvent;
import com.agentengine.engine.api.agui.CorrectionMetadata;
import com.agentengine.engine.utils.CorrectionUtils;
import com.agentengine.engine.utils.EventUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agui.core.event.*;
import com.agui.core.message.Role;
import com.google.adk.events.Event;
import com.google.genai.types.*;
import io.reactivex.rxjava3.core.Flowable;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps runtime events to AGUI events */
public final class AGUIEventMapper implements EventMapper<Event, BaseEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);

  public enum Mode {
    LIVE, REPLAY
  }

  private final Mode mode;
  private final MapperState state;

  public AGUIEventMapper(final String sessionId, final String agentId, final Mode mode) {
    this.mode = mode;
    this.state = new MapperState(sessionId, agentId);
  }

  @Override
  public Flowable<BaseEvent> map(final Event event) {
    LOG.debug("Input event received for mapping - eventId={}, author={}", event.id(), event.author());
    if ("user".equalsIgnoreCase(event.author())) {
      return mode == Mode.REPLAY ? mapUserMessage(event) : Flowable.empty();
    }
    state.currentSourceTimestamp = event.timestamp();
    state.currentSourceEventId = event.id();

    Flowable<BaseEvent> eventFlow = Flowable.empty();
    final String eventRunId = event.invocationId();
    if (eventRunId != null && !Objects.equals(state.runId, eventRunId)) {
      state.runId = eventRunId;
      RunStartedEvent startEvent = new RunStartedEvent();
      startEvent.setRunId(state.runId);
      startEvent.setThreadId(state.sessionId);

      decorateEvent(startEvent);
      LOG.debug("Generated output event - eventType=RunStartedEvent, runId={}", startEvent.getRunId());

      eventFlow = eventFlow.concatWith(Flowable.just(startEvent));
    }

    return eventFlow.concatWith(mapEventInternal(event)).concatWith(runFinishedIfNeeded(event));
  }

  @Override
  public Flowable<BaseEvent> onComplete() {
    return Flowable.empty();
  }

  @Override
  public Flowable<BaseEvent> onError(final Throwable throwable) {
    LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
    final RunErrorEvent errorEvent = new RunErrorEvent();
    errorEvent.setError(ExceptionUtils.getErrorMessage(throwable));
    errorEvent.setRawEvent(Map.of("exception", ExceptionUtils.getStackstrace(throwable)));
    final BaseEvent decoratedEvent = decorateEvent(errorEvent);
    LOG.debug("Generated output event in onError - eventType=RunErrorEvent");
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapEventInternal(final Event event) {
    LOG.debug("Processing internal mapping for event - eventId={}", event.id());
    Flowable<BaseEvent> flowable = startStepIfNeeded();
    if (CorrectionUtils.isCorrectionEvent(event)) {
      return flowable.concatWith(mapCorrectionEventIfNeeded(event));
    }

    final boolean partial = event.partial().orElse(false);

    final Optional<Content> content = event.content();
    if (content.isPresent()) {
      final List<Part> parts = content.get().parts().orElse(List.of());

      final boolean internal = EventUtils.isInternal(event);
      for (final Part part : parts) {
        if (part.thought().orElse(false) || internal) {
          final String thoughtText = part.text().orElse("");
          if (StringUtils.isNotEmpty(thoughtText)) {
            if (state.currentTextMessageId != null) {
              flowable = flowable.concatWith(finalizeTextMessageIfNeeded());
            }
            flowable = flowable.concatWith(startThinkingIfNeeded()).concatWith(startThinkingMessageIfNeeded())
                .concatWith(mapThinkingContent(thoughtText, partial)).concatWith(endThinkingMessageIfNeeded(partial));
          }
        } else {
          final String text = part.text().orElse(null);
          if (StringUtils.isNotEmpty(text)) {
            // Close thinking BEFORE text if we have both in same chunk
            flowable = flowable.concatWith(closeThinkingIfNeeded());
            flowable = flowable.concatWith(startTextMessageIfNeeded()).concatWith(mapTextMessageContent(text, partial))
                .concatWith(endTextMessageIfNeeded(partial));
          }
          final FunctionCall call = part.functionCall().orElse(null);
          if (call != null) {
            // Close thinking BEFORE tool calls
            flowable = flowable.concatWith(closeThinkingIfNeeded());
            flowable = flowable.concatWith(mapToolCall(call));
          }
          final FunctionResponse resp = part.functionResponse().orElse(null);
          if (resp != null) {
            // Close thinking BEFORE tool responses
            flowable = flowable.concatWith(closeThinkingIfNeeded());
            flowable = flowable.concatWith(mapToolResponse(resp));
          }
        }
      }
    }

    return flowable.concatWith(finishStepIfNeeded(event));
  }

  private Flowable<BaseEvent> runFinishedIfNeeded(final Event event) {
    if (event.finishReason().isEmpty()) {
      return Flowable.empty();
    }
    final RunFinishedEvent finishedEvent = buildRunFinished(state.runId);
    state.runId = null;
    decorateEvent(finishedEvent);
    LOG.debug("Generated output event - eventType=RunFinishedEvent, runId={}", finishedEvent.getRunId());
    return Flowable.just(finishedEvent);
  }

  private Flowable<BaseEvent> startStepIfNeeded() {
    if (hasStepStarted()) {
      LOG.debug("Step already started, skipping StepStartedEvent generation");
      return Flowable.empty();
    }
    state.currentStepName = nextStableStepName();
    final StepStartedEvent stepEvent = new StepStartedEvent();
    stepEvent.setStepName(state.currentStepName);
    decorateEvent(stepEvent);
    LOG.debug("Generated output event - eventType=StepStartedEvent, stepName={}", stepEvent.getStepName());
    return Flowable.just(stepEvent);
  }

  private Flowable<BaseEvent> mapCorrectionEventIfNeeded(final Event event) {
    final CorrectionMetadata correctionMetadata = CorrectionUtils.extractCorrectionMetadata(event);
    if (correctionMetadata == null) {
      return Flowable.empty();
    }
    final CorrectionEvent correctionEvent = new CorrectionEvent(correctionMetadata);
    decorateEvent(correctionEvent);
    LOG.debug("Generated correction event - correctionMetadataPresent=true");
    return Flowable.just(correctionEvent);
  }

  private Flowable<ThinkingStartEvent> startThinkingIfNeeded() {
    if (state.isThinking) {
      LOG.debug("Already in thinking state, skipping ThinkingStartEvent generation");
      return Flowable.empty();
    }
    state.isThinking = true;
    final ThinkingStartEvent thinkingStartEvent = new ThinkingStartEvent();
    decorateEvent(thinkingStartEvent);
    LOG.debug("Generated output event - eventType=ThinkingStartEvent");
    return Flowable.just(thinkingStartEvent);
  }

  private Flowable<BaseEvent> startThinkingMessageIfNeeded() {
    if (state.thinkingMessageOpen) {
      LOG.debug("Thinking message already open, skipping ThinkingTextMessageStartEvent generation");
      return Flowable.empty();
    }
    state.thinkingMessageOpen = true;
    final ThinkingTextMessageStartEvent event = new ThinkingTextMessageStartEvent();
    decorateEvent(event);
    LOG.debug("Generated output event - eventType=ThinkingTextMessageStartEvent");
    return Flowable.just(event);
  }

  private Flowable<BaseEvent> mapThinkingContent(final String text, final boolean partial) {
    if (StringUtils.isEmpty(text) || !partial) {
      return Flowable.empty();
    }
    return emitThoughtContentIfNeeded(text);
  }

  private Flowable<BaseEvent> endThinkingMessageIfNeeded(final boolean partial) {
    if (partial) {
      LOG.debug("Partial thinking message, skipping ThinkingTextMessageEndEvent generation");
      return Flowable.empty();
    }
    final ThinkingTextMessageEndEvent event = new ThinkingTextMessageEndEvent();
    decorateEvent(event);
    state.thinkingMessageOpen = false;
    LOG.debug("Generated output event - eventType=ThinkingTextMessageEndEvent");
    return Flowable.just(event);
  }

  private Flowable<ThinkingEndEvent> endThinkingIfNeeded() {
    if (!state.isThinking) {
      LOG.debug("Not in thinking state, skipping ThinkingEndEvent generation");
      return Flowable.empty();
    }
    state.isThinking = false;
    state.thinkingMessageOpen = false;
    final ThinkingEndEvent event = new ThinkingEndEvent();
    decorateEvent(event);
    LOG.debug("Generated output event - eventType=ThinkingEndEvent");
    return Flowable.just(event);
  }

  private Flowable<TextMessageStartEvent> startTextMessageIfNeeded() {
    if (state.currentTextMessageId != null) {
      LOG.debug("Text message already in progress, skipping TextMessageStartEvent generation");
      return Flowable.empty();
    }
    state.currentTextMessageId = nextStableTextMessageId();
    final TextMessageStartEvent start = new TextMessageStartEvent();
    start.setMessageId(state.currentTextMessageId);
    decorateEvent(start);
    LOG.debug("Generated output event - eventType=TextMessageStartEvent, msgId={}", start.getMessageId());
    return Flowable.just(start);
  }

  private Flowable<BaseEvent> mapTextMessageContent(final String text, final boolean partial) {
    if (StringUtils.isEmpty(text)) {
      return Flowable.empty();
    }
    LOG.debug("Processing message mapping - msgId={}, partial={}, isNewText={}", state.currentTextMessageId, partial,
        state.textBuffer.isEmpty());
    if (!partial && !state.textBuffer.isEmpty()) {
      // partial=false but chunks already streamed: skip (we've already emitted this
      // content)
      return Flowable.empty();
    }

    state.textBuffer.append(text);
    final TextMessageChunkEvent chunk = new TextMessageChunkEvent();
    chunk.setMessageId(state.currentTextMessageId);
    chunk.setDelta(text);
    decorateEvent(chunk);
    LOG.debug("Generated output event - eventType=TextMessageChunkEvent, msgId={}", chunk.getMessageId());
    return Flowable.just(chunk);
  }

  private Flowable<TextMessageEndEvent> endTextMessageIfNeeded(final boolean partial) {
    if (partial) {
      LOG.debug("Partial message, skipping TextMessageEndEvent generation");
      return Flowable.empty();
    }
    return emitTextMessageEnd();
  }

  private Flowable<TextMessageEndEvent> finalizeTextMessageIfNeeded() {
    if (state.currentTextMessageId == null) {
      return Flowable.empty();
    }
    return emitTextMessageEnd();
  }

  private Flowable<BaseEvent> emitThoughtContentIfNeeded(final String text) {
    if (!state.isThinking || !state.thinkingMessageOpen) {
      return Flowable.empty();
    }
    final ThinkingTextMessageContentEvent content = new ThinkingTextMessageContentEvent();
    content.setRawEvent(Map.of("delta", text));
    decorateEvent(content);
    LOG.debug("Generated output event - eventType=ThinkingTextMessageContentEvent");
    return Flowable.just(content);
  }

  private Flowable<TextMessageEndEvent> emitTextMessageEnd() {
    if (state.currentTextMessageId == null) {
      return Flowable.empty();
    }
    state.finalAnswer = state.textBuffer.toString();
    final TextMessageEndEvent end = new TextMessageEndEvent();
    end.setMessageId(state.currentTextMessageId);
    decorateEvent(end);
    LOG.debug("Generated output event - eventType=TextMessageEndEvent, msgId={}", end.getMessageId());
    resetTextMessageState();
    return Flowable.just(end);
  }

  private Flowable<BaseEvent> closeThinkingIfNeeded() {
    if (!state.isThinking) {
      return Flowable.empty();
    }
    Flowable<BaseEvent> flowable = Flowable.empty();
    if (state.thinkingMessageOpen) {
      flowable = flowable.concatWith(endThinkingMessageIfNeeded(false));
    }
    return flowable.concatWith(endThinkingIfNeeded());
  }

  private void resetTextMessageState() {
    state.currentTextMessageId = null;
    state.textBuffer = new StringBuilder();
  }

  private boolean hasStepStarted() {
    return StringUtils.isNotBlank(state.currentStepName);
  }

  private String nextStableStepName() {
    return stableReplayId("step-", state.currentSourceEventId, ++state.stepSequence);
  }

  private String nextStableTextMessageId() {
    return stableReplayId("msg-", state.currentSourceEventId, ++state.textMessageSequence);
  }

  private static String stableReplayId(final String prefix, final String sourceEventId, final int sequence) {
    if (StringUtils.isNotBlank(sourceEventId)) {
      return prefix + sourceEventId;
    }
    return prefix + sequence;
  }

  private Flowable<StepFinishedEvent> finishStepIfNeeded(final Event event) {
    if (!event.turnComplete().orElse(false) || !hasStepStarted()) {
      return Flowable.empty();
    }
    return finishStep();
  }

  private Flowable<StepFinishedEvent> finishStep() {
    return Flowable.defer(() -> {
      final String stepName = state.currentStepName;
      state.currentStepName = null;
      final StepFinishedEvent stepEvent = new StepFinishedEvent();
      stepEvent.setStepName(stepName);
      decorateEvent(stepEvent);
      LOG.debug("Generated output event - eventType=StepFinishedEvent, stepName={}", stepEvent.getStepName());
      return Flowable.just(stepEvent);
    });
  }

  private Flowable<BaseEvent> mapUserMessage(final Event event) {
    final String text = event.content()
        .flatMap(c -> c.parts().flatMap(parts -> parts.stream().map(p -> p.text().orElse("")).filter(StringUtils::isNotBlank).findFirst()))
        .orElse(null);
    final String messageId = stableReplayId("msg-", event.id(), ++state.textMessageSequence);

    final TextMessageStartEvent start = new TextMessageStartEvent();
    start.setMessageId(messageId);
    start.setRole("user");
    decorateEvent(start);

    final TextMessageContentEvent content = new TextMessageContentEvent();
    content.setMessageId(messageId);
    content.setDelta(text);
    decorateEvent(content);

    final TextMessageEndEvent end = new TextMessageEndEvent();
    end.setMessageId(messageId);
    decorateEvent(end);

    LOG.debug("Generated user message events for replay - msgId={}", messageId);
    return Flowable.just(start, content, end);
  }

  private Flowable<BaseEvent> mapToolCall(final FunctionCall call) {
    final String callId = call.id().orElseGet(() -> UUID.randomUUID().toString());
    final String toolName = call.name().orElse("unknown");
    final Map<String, Object> args = call.args().orElse(Map.of());

    Flowable<BaseEvent> flowable = Flowable.empty();
    LOG.debug("Processing tool call mapping - callId='{}', toolName='{}'", callId, toolName);
    final ToolCallStartEvent start = new ToolCallStartEvent();
    start.setToolCallId(callId);
    start.setToolCallName(toolName);
    flowable = flowable.concatWith(Flowable.just(decorateEvent(start)));

    final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
    argsEvent.setToolCallId(callId);
    argsEvent.setDelta(JsonUtils.toJson(args));
    flowable = flowable.concatWith(Flowable.just(decorateEvent(argsEvent)));

    final ToolCallEndEvent end = new ToolCallEndEvent();
    end.setToolCallId(callId);
    return flowable.concatWith(Flowable.just(decorateEvent(end)));
  }

  private Flowable<BaseEvent> mapToolResponse(final FunctionResponse response) {
    final String callId = response.id().orElse(UUID.randomUUID().toString());
    final String contentResult = JsonUtils.toJson(response.response().orElse(Map.of()));

    LOG.debug("Processing tool response mapping - callId='{}'", callId);

    final ToolCallResultEvent result = new ToolCallResultEvent();
    result.setToolCallId(callId);
    result.setContent(contentResult);
    result.setRole(Role.tool);

    final BaseEvent decoratedResult = decorateEvent(result);
    LOG.debug("Generated output event - eventType=ToolCallResultEvent, callId={}", result.getToolCallId());
    return Flowable.just(decoratedResult);
  }

  private RunFinishedEvent buildRunFinished(final String runId) {
    final RunFinishedEvent event = new RunFinishedEvent();
    event.setThreadId(state.sessionId);
    event.setRunId(runId);
    if (state.finalAnswer != null) {
      event.setResult(state.finalAnswer);
    }
    LOG.debug("Building RunFinishedEvent - runId='{}', sessionId='{}'", runId, state.sessionId);
    return event;
  }

  private <T extends BaseEvent> T decorateEvent(final T event) {
    final long sourceTimestamp = state.currentSourceTimestamp;
    event.setTimestamp(sourceTimestamp > 0 ? sourceTimestamp : System.currentTimeMillis());
    // noinspection unchecked
    final Map<String, Object> rawEvent = CollectionUtils.nullSafeMutableMap((Map<String, Object>) event.getRawEvent());
    event.setRawEvent(null);
    rawEvent.putAll(JsonUtils.toMap(event));
    rawEvent.put("agentId", state.agentId);
    rawEvent.put("threadId", state.sessionId);
    event.setRawEvent(rawEvent);
    LOG.debug("Decorated event - eventType='{}', eventId='{}', agentId='{}'", event.getClass().getSimpleName(), getEventId(event),
        state.agentId);
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
    } else if (event instanceof ThinkingTextMessageStartEvent || event instanceof ThinkingTextMessageContentEvent
        || event instanceof ThinkingTextMessageEndEvent) {
      return "thinking-text";
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
    private boolean isThinking;
    private boolean thinkingMessageOpen;
    private String finalAnswer;
    private long currentSourceTimestamp;
    private String currentSourceEventId;
    private int stepSequence;
    private int textMessageSequence;
    private StringBuilder textBuffer = new StringBuilder();

    private MapperState(final String sessionId, final String agentId) {
      this.sessionId = sessionId;
      this.agentId = agentId;
    }
  }
}

package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.CorrectionMetadata;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.runtime.actor.SessionEventUtils;
import com.agentengine.util.common.*;
import com.agui.core.event.*;
import com.agui.core.message.Role;
import com.google.genai.types.*;
import io.reactivex.rxjava3.core.Flowable;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps runtime SessionEvent to AGUI events */
public final class AGUIEventMapper implements EventMapper<SessionEvent, BaseEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);

  public enum Mode {
    LIVE, REPLAY
  }

  private final Mode mode;
  private final MapperState state;

  public AGUIEventMapper(final String sessionId, final String agentId) {
    this(sessionId, agentId, Mode.LIVE);
  }

  public AGUIEventMapper(final String sessionId, final String agentId, final Mode mode) {
    this.mode = mode;
    this.state = new MapperState(sessionId, agentId);
  }

  @Override
  public Flowable<BaseEvent> map(final SessionEvent event) {
    LOG.debug("Input event received for mapping - eventId={}, author={}", event.id(), event.author());
    if ("user".equalsIgnoreCase(event.author())) {
      return mode == Mode.REPLAY ? mapUserMessage(event) : Flowable.empty();
    }
    state.currentSourceTimestamp = event.timestamp();
    state.currentSourceEventId = event.id();

    Flowable<BaseEvent> eventFlow = Flowable.empty();
    final String eventRunId = event.runId();
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

  private Flowable<BaseEvent> mapEventInternal(final SessionEvent event) {
    LOG.debug("Processing internal mapping for SessionEvent - eventId={}", event.id());
    Flowable<BaseEvent> flowable = startStepIfNeeded();

    if (SessionEventUtils.isCorrectionEvent(event)) {
      flowable = flowable.concatWith(mapCorrectionEventIfNeeded(event));
    } else {
      final boolean partial = event.partial() != null && event.partial();
      final Optional<Content> content = Optional.ofNullable(event.content());

      if (content.isPresent()) {
        final List<Part> parts = content.get().parts().orElse(List.of());
        final boolean internal = SessionEventUtils.isInternal(event);;

        for (final Part part : parts) {
          if (part.thought().orElse(false) || internal) {
            final String thoughtText = part.text().orElse("");
            if (StringUtils.isNotEmpty(thoughtText)) {
              if (state.currentTextMessageId != null) {
                flowable = flowable.concatWith(finalizeTextMessageIfNeeded());
              }
              flowable = flowable.concatWith(startReasoningIfNeeded()).concatWith(startReasoningMessageIfNeeded())
                  .concatWith(mapReasoningContent(thoughtText));
              if (!partial) {
                // Non-streaming model delivered thought content in a single event — close the blocks immediately.
                flowable = flowable.concatWith(endReasoningMessageIfNeeded()).concatWith(endReasoningIfNeeded());
              }
            }
          } else {
            final String text = part.text().orElse(null);
            if (StringUtils.isNotEmpty(text)) {
              // Close reasoning BEFORE text if we have both in same chunk
              flowable = flowable.concatWith(closeReasoningIfNeeded());
              flowable = flowable.concatWith(startTextMessageIfNeeded()).concatWith(mapTextMessageContent(text, partial))
                  .concatWith(endTextMessageIfNeeded(partial));
            }
            final FunctionCall call = part.functionCall().orElse(null);
            if (call != null) {
              // Close reasoning BEFORE tool calls
              flowable = flowable.concatWith(closeReasoningIfNeeded());
              flowable = flowable.concatWith(mapToolCall(call));
            }
            final FunctionResponse resp = part.functionResponse().orElse(null);
            if (resp != null) {
              // Close reasoning BEFORE tool responses
              flowable = flowable.concatWith(closeReasoningIfNeeded());
              flowable = flowable.concatWith(mapToolResponse(resp));
            }
          }
        }
      }
    }

    return flowable.concatWith(finishStepIfNeeded(event));
  }

  private Flowable<BaseEvent> runFinishedIfNeeded(final SessionEvent event) {
    if (event.finishReason() == null) {
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

  private Flowable<BaseEvent> mapCorrectionEventIfNeeded(final SessionEvent event) {
    final CorrectionMetadata correctionMetadata = SessionEventUtils.extractCorrectionMetadata(event);
    if (correctionMetadata == null) {
      return Flowable.empty();
    }
    final CorrectionEvent correctionEvent = new CorrectionEvent(correctionMetadata);
    decorateEvent(correctionEvent);
    LOG.debug("Generated correction event - correctionMetadataPresent=true");
    return Flowable.just(correctionEvent);
  }

  private Flowable<BaseEvent> startReasoningIfNeeded() {
    if (state.currentReasoningId != null) {
      LOG.debug("Already in reasoning state, skipping ReasoningStartEvent generation");
      return Flowable.empty();
    }
    state.currentReasoningId = nextStableReasoningId();
    final ReasoningStartEvent event = new ReasoningStartEvent();
    event.setMessageId(state.currentReasoningId);
    decorateEvent(event);
    LOG.debug("Generated output event - eventType=ReasoningStartEvent, messageId={}", state.currentReasoningId);
    return Flowable.just(event);
  }

  private Flowable<BaseEvent> startReasoningMessageIfNeeded() {
    if (state.currentReasoningMessageId != null) {
      LOG.debug("Reasoning message already open, skipping ReasoningMessageStartEvent generation");
      return Flowable.empty();
    }
    state.currentReasoningMessageId = nextStableReasoningMessageId();
    final ReasoningMessageStartEvent event = new ReasoningMessageStartEvent();
    event.setMessageId(state.currentReasoningMessageId);
    event.setRole("assistant");
    decorateEvent(event);
    LOG.debug("Generated output event - eventType=ReasoningMessageStartEvent, messageId={}", state.currentReasoningMessageId);
    return Flowable.just(event);
  }

  private Flowable<BaseEvent> mapReasoningContent(final String text) {
    if (StringUtils.isEmpty(text) || state.currentReasoningMessageId == null) {
      return Flowable.empty();
    }
    final ReasoningMessageContentEvent event = new ReasoningMessageContentEvent();
    event.setMessageId(state.currentReasoningMessageId);
    event.setDelta(text);
    decorateEvent(event);
    LOG.debug("Generated output event - eventType=ReasoningMessageContentEvent");
    return Flowable.just(event);
  }

  private Flowable<BaseEvent> endReasoningMessageIfNeeded() {
    if (state.currentReasoningMessageId == null) {
      return Flowable.empty();
    }
    final ReasoningMessageEndEvent event = new ReasoningMessageEndEvent();
    event.setMessageId(state.currentReasoningMessageId);
    decorateEvent(event);
    state.currentReasoningMessageId = null;
    LOG.debug("Generated output event - eventType=ReasoningMessageEndEvent");
    return Flowable.just(event);
  }

  private Flowable<BaseEvent> endReasoningIfNeeded() {
    if (state.currentReasoningId == null) {
      return Flowable.empty();
    }
    final ReasoningEndEvent event = new ReasoningEndEvent();
    event.setMessageId(state.currentReasoningId);
    decorateEvent(event);
    state.currentReasoningId = null;
    state.currentReasoningMessageId = null;
    LOG.debug("Generated output event - eventType=ReasoningEndEvent");
    return Flowable.just(event);
  }

  private Flowable<BaseEvent> closeReasoningIfNeeded() {
    if (state.currentReasoningId == null) {
      return Flowable.empty();
    }
    return endReasoningMessageIfNeeded().concatWith(endReasoningIfNeeded());
  }

  private Flowable<TextMessageStartEvent> startTextMessageIfNeeded() {
    if (state.currentTextMessageId != null) {
      LOG.debug("Text message already in progress, skipping TextMessageStartEvent generation");
      return Flowable.empty();
    }
    state.currentTextMessageId = nextStableTextMessageId();
    final TextMessageStartEvent start = new TextMessageStartEvent();
    start.setMessageId(state.currentTextMessageId);
    start.setRole("assistant");
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
    if (!partial) {
      // Non-partial events repeat the accumulated content — only buffer the text if no
      // streaming chunks have arrived yet (i.e., this is a non-streaming response).
      if (state.textBuffer.isEmpty()) {
        state.textBuffer.append(text);
      }
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

  private Flowable<BaseEvent> endTextMessageIfNeeded(final boolean partial) {
    if (partial) {
      LOG.debug("Partial message, skipping TextMessageEndEvent generation");
      return Flowable.empty();
    }
    return emitTextMessageEnd();
  }

  private Flowable<BaseEvent> finalizeTextMessageIfNeeded() {
    if (state.currentTextMessageId == null) {
      return Flowable.empty();
    }
    return emitTextMessageEnd();
  }

  private Flowable<BaseEvent> emitTextMessageEnd() {
    if (state.currentTextMessageId == null) {
      return Flowable.empty();
    }
    state.finalAnswer = state.textBuffer.toString();
    Flowable<BaseEvent> flowable = Flowable.empty();
    if (StringUtils.isNotBlank(state.finalAnswer)) {
      final TextMessageContentEvent content = new TextMessageContentEvent();
      content.setMessageId(state.currentTextMessageId);
      content.setDelta(state.finalAnswer);
      decorateEvent(content);
      LOG.debug("Generated output event - eventType=TextMessageContentEvent, msgId={}", content.getMessageId());
      flowable = flowable.concatWith(Flowable.just(content));
    }
    final TextMessageEndEvent end = new TextMessageEndEvent();
    end.setMessageId(state.currentTextMessageId);
    decorateEvent(end);
    LOG.debug("Generated output event - eventType=TextMessageEndEvent, msgId={}", end.getMessageId());
    resetTextMessageState();
    return flowable.concatWith(Flowable.just(end));
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

  private String nextStableReasoningId() {
    return stableReplayId("reasoning-", state.currentSourceEventId, ++state.reasoningSequence);
  }

  private String nextStableReasoningMessageId() {
    return stableReplayId("reasoning-msg-", state.currentSourceEventId, ++state.reasoningMessageSequence);
  }

  private static String stableReplayId(final String prefix, final String sourceEventId, final int sequence) {
    if (StringUtils.isNotBlank(sourceEventId)) {
      return prefix + sourceEventId + "-" + sequence;
    }
    return prefix + sequence;
  }

  private Flowable<BaseEvent> finishStepIfNeeded(final SessionEvent event) {
    final boolean turnComplete = event.turnComplete() != null && event.turnComplete();
    if (!turnComplete || !hasStepStarted()) {
      return Flowable.empty();
    }
    return finishStep();
  }

  private Flowable<BaseEvent> finishStep() {
    final String stepName = state.currentStepName;
    state.currentStepName = null;
    state.toolCallParentSteps.clear();
    final StepFinishedEvent stepEvent = new StepFinishedEvent();
    stepEvent.setStepName(stepName);
    decorateEvent(stepEvent);
    LOG.debug("Generated output event - eventType=StepFinishedEvent, stepName={}", stepEvent.getStepName());
    // ReasoningEnd is a higher-level lifecycle event that spans multiple partial thought
    // blocks — unlike TextMessageEnd and ReasoningMessageEnd, it is not tied to any
    // content arriving, so it must be explicitly closed here before the step finishes.
    return finalizeTextMessageIfNeeded().concatWith(closeReasoningIfNeeded()).concatWith(Flowable.just(stepEvent));
  }

  private Flowable<BaseEvent> mapUserMessage(final SessionEvent event) {
    final String text = event.content() != null 
        ? event.content().parts().flatMap(parts -> parts.stream().map(p -> p.text().orElse("")).filter(StringUtils::isNotBlank).findFirst())
            .orElse(null)
        : null;
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

    // Track the step that issued this tool call so ToolCallResult can reference it
    state.toolCallParentSteps.put(callId, state.currentStepName);

    Flowable<BaseEvent> flowable = Flowable.empty();
    LOG.debug("Processing tool call mapping - callId='{}', toolName='{}'", callId, toolName);
    final ToolCallStartEvent start = new ToolCallStartEvent();
    start.setToolCallId(callId);
    start.setToolCallName(toolName);
    start.setParentMessageId(state.currentStepName);
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
    result.setMessageId(state.toolCallParentSteps.remove(callId));

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

  private String getEventId(final BaseEvent event) {
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
    } else if (event instanceof ReasoningStartEvent e) {
      return e.getMessageId();
    } else if (event instanceof ReasoningEndEvent e) {
      return e.getMessageId();
    } else if (event instanceof ReasoningMessageStartEvent e) {
      return e.getMessageId();
    } else if (event instanceof ReasoningMessageContentEvent e) {
      return e.getMessageId();
    } else if (event instanceof ReasoningMessageEndEvent e) {
      return e.getMessageId();
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
    private String currentReasoningId;
    private String currentReasoningMessageId;
    private String finalAnswer;
    private long currentSourceTimestamp;
    private String currentSourceEventId;
    private int stepSequence;
    private int textMessageSequence;
    private int reasoningSequence;
    private int reasoningMessageSequence;
    private StringBuilder textBuffer = new StringBuilder();
    private final Map<String, String> toolCallParentSteps = new HashMap<>();

    private MapperState(final String sessionId, final String agentId) {
      this.sessionId = sessionId;
      this.agentId = agentId;
    }
  }
}

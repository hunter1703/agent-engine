package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.utils.*;
import com.agentengine.interfaces.rest.dto.CorrectionEvent;
import com.agui.core.event.*;
import com.agui.core.message.Role;
import com.google.adk.events.Event;
import com.google.genai.types.*;
import io.reactivex.rxjava3.core.Flowable;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps ADK events to AGUI events */
public final class AGUIEventMapper implements EventMapper<Event, BaseEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);
  private final MapperState state;

  public AGUIEventMapper(final String sessionId, final String agentId) {
    this.state = new MapperState(sessionId, agentId);
  }

  @Override
  public Flowable<BaseEvent> map(final Event event) {
    LOG.debug("Input event received for mapping - event={}", JsonUtils.toJson(event));

    Flowable<BaseEvent> eventFlow = Flowable.empty();
    if (state.runId == null) {
      state.runId = event.invocationId();
      RunStartedEvent startEvent = new RunStartedEvent();
      startEvent.setRunId(state.runId);
      startEvent.setThreadId(state.sessionId);

      decorateEvent(startEvent);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(startEvent));

      eventFlow = eventFlow.concatWith(Flowable.just(startEvent));
    }

    return eventFlow.concatWith(mapEventInternal(event));
  }

  @Override
  public Flowable<BaseEvent> onComplete() {
    Flowable<BaseEvent> flowable = Flowable.empty();
    flowable = flowable.concatWith(mapStepFinish(true));
    final BaseEvent decoratedEvent = decorateEvent(buildRunFinished(state.runId));
    LOG.debug("Generated output event in onComplete - event={}", JsonUtils.toJson(decoratedEvent));
    return flowable.concatWith(Flowable.just(decoratedEvent));
  }

  @Override
  public Flowable<BaseEvent> onError(final Throwable throwable) {
    LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
    final RunErrorEvent errorEvent = new RunErrorEvent();
    errorEvent.setError(ExceptionUtils.getErrorMessage(throwable));
    errorEvent.setRawEvent(Map.of("exception", ExceptionUtils.getStackstrace(throwable)));
    final BaseEvent decoratedEvent = decorateEvent(errorEvent);
    LOG.debug("Generated output event in onError - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapEventInternal(final Event event) {
    LOG.debug("Processing internal mapping for event - event={}", JsonUtils.toJson(event));
    Flowable<BaseEvent> flowable = Flowable.empty();
    flowable = flowable.concatWith(mapStepStart());
    if (CorrectionUtils.isCorrectionEvent(event)) {
      return flowable.concatWith(mapCorrectionEvent(event));
    }

    final Content content = event.content().orElse(null);
    if (content != null) {
      final boolean partial = event.partial().orElse(false);
      final List<Part> parts = content.parts().orElse(List.of());

      final List<Part> thoughtsParts = parts.stream().filter(part -> part.thought().orElse(false)).toList();
      final List<Part> textParts = parts.stream().filter(part -> !part.thought().orElse(false)).toList();
      final List<FunctionCall> toolCalls = parts.stream().map(Part::functionCall).map(optional -> optional.orElse(null)).filter(Objects::nonNull).toList();
      final List<FunctionResponse> toolResponses = parts.stream().map(Part::functionResponse).map(optional -> optional.orElse(null)).filter(Objects::nonNull).toList();

      for (final Part part : thoughtsParts) {
        final String thoughtText = part.text().orElse("");
        if (StringUtils.isNotBlank(thoughtText)) {
          LOG.debug("Mapping thought part - text='{}', partial={}", thoughtText, partial);
          flowable = flowable.concatWith(mapThinkingStart()).concatWith(mapThinkingMessageStart()).concatWith(mapThinkingContent(thoughtText, partial)).concatWith(mapThinkingMessageEnd(partial));
        }
      }

      flowable = flowable.concatWith(mapThinkingEnd());

      for (final Part part : textParts) {
        final String text = part.text().orElse("");
        if (StringUtils.isNotBlank(text)) {
          LOG.debug("Mapping text part - text='{}', partial={}", text, partial);
          flowable = flowable.concatWith(mapTextMessageStartEvent()).concatWith(mapTextMessageContent(text, partial)).concatWith(mapTextMessageEndEvent(partial));
        }
      }

      for (final FunctionCall toolCall : toolCalls) {
        flowable = flowable.concatWith(mapToolCall(toolCall));
      }

      for (final FunctionResponse toolResponse : toolResponses) {
        flowable = flowable.concatWith(mapToolResponse(toolResponse));
      }
    }

    return flowable.concatWith(mapStepFinish(event.turnComplete().orElse(false)));
  }

  private Flowable<BaseEvent> mapStepStart() {
    if (hasStepStarted()) {
      LOG.debug("Step already started, skipping StepStartedEvent generation");
      return Flowable.empty();
    }
    state.currentStepName = "step-" + UUID.randomUUID();
    final StepStartedEvent stepEvent = new StepStartedEvent();
    stepEvent.setStepName(state.currentStepName);
    decorateEvent(stepEvent);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(stepEvent));
    return Flowable.just(stepEvent);
  }

  private Flowable<BaseEvent> mapCorrectionEvent(final Event event) {
    final CorrectionMetadata correctionMetadata = CorrectionUtils.extractCorrectionMetadata(event);
    if (correctionMetadata == null) {
      return Flowable.empty();
    }
    CorrectionEvent correctionEvent = new CorrectionEvent(correctionMetadata);
    decorateEvent(correctionEvent);
    LOG.debug("Generated correction event - event={}", JsonUtils.toJson(correctionEvent));
    return Flowable.just(correctionEvent);
  }

  private Flowable<ThinkingStartEvent> mapThinkingStart() {
    if (state.isThinking) {
      LOG.debug("Already in thinking state, skipping ThinkingStartEvent generation");
      return Flowable.empty();
    }
    state.isThinking = true;
    final ThinkingStartEvent thinkingStartEvent = new ThinkingStartEvent();
    decorateEvent(thinkingStartEvent);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(thinkingStartEvent));
    return Flowable.just(thinkingStartEvent);
  }

  private Flowable<BaseEvent> mapThinkingMessageStart() {
    if (state.thinkingMessageOpen) {
      LOG.debug("Thinking message already open, skipping ThinkingTextMessageStartEvent generation");
      return Flowable.empty();
    }
    state.thinkingMessageOpen = true;
    final ThinkingTextMessageStartEvent event = new ThinkingTextMessageStartEvent();
    decorateEvent(event);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(event));
    return Flowable.just(event);
  }

  private Flowable<BaseEvent> mapThinkingContent(final String text, final boolean partial) {
    final ThinkingTextMessageContentEvent content = new ThinkingTextMessageContentEvent();
    final Map<String, Object> rawEvent = Map.of("delta", text, "partial", partial);
    content.setRawEvent(rawEvent);
    decorateEvent(content);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(content));
    return Flowable.just(content);
  }

  private Flowable<BaseEvent> mapThinkingMessageEnd(final boolean partial) {
    if (partial) {
        LOG.debug("Partial thinking message, skipping ThinkingTextMessageEndEvent generation");
        return Flowable.empty();
    }
    final ThinkingTextMessageEndEvent event = new ThinkingTextMessageEndEvent();
    decorateEvent(event);
    state.thinkingMessageOpen = false;
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(event));
    return Flowable.just(event);
  }

  private Flowable<ThinkingEndEvent> mapThinkingEnd() {
    if (!state.isThinking) {
      LOG.debug("Not in thinking state, skipping ThinkingEndEvent generation");
      return Flowable.empty();
    }
    state.isThinking = false;
    final ThinkingEndEvent event = new ThinkingEndEvent();
    decorateEvent(event);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(event));
    return Flowable.just(event);
  }

  private Flowable<TextMessageStartEvent> mapTextMessageStartEvent() {
    if (state.currentTextMessageId != null) {
      LOG.debug("Text message already in progress, skipping TextMessageStartEvent generation");
      return Flowable.empty();
    }
    state.currentTextMessageId = "msg-" + UUID.randomUUID();
    final TextMessageStartEvent start = new TextMessageStartEvent();
    start.setMessageId(state.currentTextMessageId);
    decorateEvent(start);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(start));
    return Flowable.just(start);
  }

  private Flowable<BaseEvent> mapTextMessageContent(String text, final boolean partial) {
    LOG.debug("Processing message mapping - text='{}', partial={}", text, partial);
    if (partial) {
      final TextMessageChunkEvent chunk = new TextMessageChunkEvent();
      chunk.setMessageId(state.currentTextMessageId);
      chunk.setDelta(text);
      decorateEvent(chunk);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(chunk));
      return Flowable.just(chunk);
    } else {
      final TextMessageContentEvent content = new TextMessageContentEvent();
      content.setMessageId(state.currentTextMessageId);
      content.setDelta(text);
      decorateEvent(content);
      state.finalAnswer = text;
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(content));
      return Flowable.just(content);
    }
  }

  private Flowable<TextMessageEndEvent> mapTextMessageEndEvent(final boolean partial) {
    if (partial) {
        LOG.debug("Partial message, skipping TextMessageEndEvent generation");
        return Flowable.empty();
    }
    final TextMessageEndEvent end = new TextMessageEndEvent();
    end.setMessageId(state.currentTextMessageId);
    decorateEvent(end);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(end));
    state.currentTextMessageId = null;
    return Flowable.just(end);
  }

  private boolean hasStepStarted() {
    return StringUtils.isNotBlank(state.currentStepName);
  }

  private Flowable<BaseEvent> mapStepFinish(boolean turnComplete) {
    if (!turnComplete) {
      return Flowable.empty();
    }
    final StepFinishedEvent stepEvent = new StepFinishedEvent();
    stepEvent.setStepName(state.currentStepName);
    state.currentStepName = null;
    decorateEvent(stepEvent);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(stepEvent));
    return Flowable.just(stepEvent);
  }

  private Flowable<BaseEvent> mapToolCall(final FunctionCall call) {
    final String callId = call.id().orElseGet(() -> UUID.randomUUID().toString());
    final String toolName = call.name().orElse("unknown");
    final Map<String, Object> args = call.args().orElse(Map.of());
 
    Flowable<BaseEvent> flowable = Flowable.empty();
    LOG.debug(
            "Processing tool call mapping - callId='{}', toolName='{}', args={}",
            callId,
            toolName,
            JsonUtils.toJson(args));
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

    LOG.debug(
        "Processing tool response mapping - callId='{}', contentResult='{}'",
        callId,
        contentResult);

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
    LOG.debug(
        "Building RunFinishedEvent - runId='{}', sessionId='{}', finalAnswer='{}'",
        runId,
        state.sessionId,
        state.finalAnswer);
    return event;
  }

  private <T extends BaseEvent> T decorateEvent(final T event) {
    event.setTimestamp(System.currentTimeMillis());
    //noinspection unchecked
    final Map<String, Object> rawEvent = CollectionUtils.nullSafeMutableMap((Map<String, Object>) event.getRawEvent());
    event.setRawEvent(null);
    rawEvent.putAll(JsonUtils.toMap(event));
    rawEvent.put("agentId", state.agentId);
    event.setRawEvent(rawEvent);
    LOG.debug(
        "Decorated event - eventType='{}', eventId='{}', agentId='{}'",
        event.getClass().getSimpleName(),
        getEventId(event),
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
    } else if (event instanceof ThinkingTextMessageStartEvent
        || event instanceof ThinkingTextMessageContentEvent
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

    private MapperState(final String sessionId, final String agentId) {
      this.sessionId = sessionId;
      this.agentId = agentId;
    }
  }
}

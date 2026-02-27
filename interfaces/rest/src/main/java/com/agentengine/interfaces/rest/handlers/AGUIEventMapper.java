package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.ExceptionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.utils.CorrectionUtils;
import com.agui.core.event.*;
import com.agui.core.message.Role;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    errorEvent.setRawEvent(Map.of("exception", ExceptionUtils.getStackstrace(throwable)));
    final BaseEvent decoratedEvent = decorateEvent(errorEvent);
    LOG.debug("Generated output event in onError - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapEventInternal(final Event event) {
    LOG.debug("Processing internal mapping for event - event={}", JsonUtils.toJson(event));

    if (CorrectionUtils.isCorrectionEvent(event)) {
      return mapCorrectionEvent(event);
    }

    final List<Flowable<BaseEvent>> flows = new ArrayList<>();
    flows.add(mapStepStart());

    final Content content = event.content().orElse(null);
    if (content != null) {
      final boolean partial = event.partial().orElse(false);
      final List<Part> parts = content.parts().orElse(List.of());

      for (Part part : parts) {
        final String text = part.text().orElse(null);
        if (text != null) {
          final boolean isThought = part.thought().orElse(false);
          LOG.debug("Mapping text part - text='{}', isThought={}, partial={}", text, isThought, partial);
          if (isThought) {
            flows.add(mapThinking(text, partial));
          } else {
            flows.add(mapMessage(text, partial));
          }
        }

        part.functionCall().ifPresent(call -> flows.add(mapToolCall(call)));
        part.functionResponse().ifPresent(response -> flows.add(mapToolResponse(response)));
      }
    }

    flows.add(mapStepFinish(event));

    return Flowable.concat(flows);
  }

  private Flowable<BaseEvent> mapStepStart() {
    if (hasStepStarted()) {
      LOG.debug("Step already started, skipping StepStartedEvent generation");
      return Flowable.empty();
    }
    state.currentStepName = "step-" + UUID.randomUUID();
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
    if (!hasStepStarted()) {
      return Flowable.empty();
    }
    final boolean turnComplete =
        event.turnComplete().orElse(!event.partial().orElse(false));
    if (!turnComplete) {
      return Flowable.empty();
    }
    final StepFinishedEvent stepEvent = new StepFinishedEvent();
    stepEvent.setStepName(state.currentStepName);
    state.currentStepName = null;
    final BaseEvent decoratedStepEvent = decorateEvent(stepEvent);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedStepEvent));
    return Flowable.just(decoratedStepEvent);
  }

  private Flowable<BaseEvent> mapThinking(final String text, final boolean partial) {
    if (StringUtils.isBlank(text)) {
      LOG.debug("Skipping thinking mapping due to blank text - text='{}'", text);
      return Flowable.empty();
    }

    LOG.debug("Processing thinking mapping - text='{}', partial={}", text, partial);

    final List<Flowable<BaseEvent>> flows = new ArrayList<>();
    if (!state.isThinking) {
      flows.add(mapThinkingStart());
    }

    if (!state.thinkingMessageOpen) {
      flows.add(mapThinkingMessageStart());
    }

    if (partial) {
      final String delta = resolveDelta(text, state.lastSentThinking);
      if (StringUtils.isNotBlank(delta)) {
        state.lastSentThinking = text;
        flows.add(mapThinkingContent(delta, true));
      }
    } else {
      state.lastSentThinking = text;
      flows.add(mapThinkingContent(text, false));
      flows.add(mapThinkingMessageEnd());
      state.isThinking = false;
      flows.add(Flowable.just(decorateEvent(new ThinkingEndEvent())));
      state.lastSentThinking = null;
    }

    return Flowable.concat(flows);
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

  private Flowable<BaseEvent> mapThinkingMessageStart() {
    if (state.thinkingMessageOpen) {
      return Flowable.empty();
    }
    state.thinkingMessageOpen = true;
    final BaseEvent decoratedEvent = decorateEvent(new ThinkingTextMessageStartEvent());
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapThinkingMessageEnd() {
    if (!state.thinkingMessageOpen) {
      return Flowable.empty();
    }
    state.thinkingMessageOpen = false;
    final BaseEvent decoratedEvent = decorateEvent(new ThinkingTextMessageEndEvent());
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapThinkingContent(final String text, final boolean chunk) {
    final ThinkingTextMessageContentEvent content = new ThinkingTextMessageContentEvent();
    final Map<String, Object> rawEvent = CollectionUtils.nullSafeMutableMap(Map.of("delta", text));
    if (chunk) {
      rawEvent.put("chunk", true);
    }
    final BaseEvent decoratedEvent = decorateEvent(content, rawEvent);
    LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private Flowable<BaseEvent> mapMessage(String text, final boolean partial) {
    if (StringUtils.isBlank(text)) {
      LOG.debug("Skipping message mapping due to blank text - text='{}'", text);
      return Flowable.empty();
    }

    LOG.debug("Processing message mapping - text='{}', partial={}", text, partial);

    final List<Flowable<BaseEvent>> flows = new ArrayList<>();
    if (state.currentTextMessageId == null) {
      state.currentTextMessageId = "msg-" + UUID.randomUUID().toString();
      final TextMessageStartEvent start = new TextMessageStartEvent();
      start.setMessageId(state.currentTextMessageId);
      final BaseEvent decoratedStart = decorateEvent(start);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedStart));
      flows.add(Flowable.just(decoratedStart));
    }

    if (partial) {
      final String delta = resolveDelta(text, state.lastSentText);
      if (StringUtils.isBlank(delta)) {
        LOG.debug(
            "Skipping message chunk mapping due to empty delta - text='{}', lastSentText='{}'",
            text,
            state.lastSentText);
        return Flowable.empty();
      }

      state.lastSentText = text;
      final TextMessageChunkEvent chunk = new TextMessageChunkEvent();
      chunk.setMessageId(state.currentTextMessageId);
      chunk.setDelta(delta);
      final BaseEvent decoratedChunk = decorateEvent(chunk);
      LOG.debug("Generated output event - event={}", JsonUtils.toJson(decoratedChunk));
      flows.add(Flowable.just(decoratedChunk));
    } else {
      state.lastSentText = text;
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
      state.lastSentText = null;
    }

    return Flowable.concat(flows);
  }

  private Flowable<BaseEvent> mapToolCall(final FunctionCall call) {
    final String callId = call.id().orElseGet(() -> UUID.randomUUID().toString());
    final String toolName = call.name().orElse("unknown");
    final Map<String, Object> args = call.args().orElse(Map.of());
 
    final List<Flowable<BaseEvent>> flows = new ArrayList<>();
    LOG.debug(
            "Processing tool call mapping - callId='{}', toolName='{}', args={}",
            callId,
            toolName,
            JsonUtils.toJson(args));
    final ToolCallStartEvent start = new ToolCallStartEvent();
    start.setToolCallId(callId);
    start.setToolCallName(toolName);
    flows.add(Flowable.just(decorateEvent(start)));

    final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
    argsEvent.setToolCallId(callId);
    argsEvent.setDelta(JsonUtils.toJson(args));
    flows.add(Flowable.just(decorateEvent(argsEvent)));

    final ToolCallEndEvent end = new ToolCallEndEvent();
    end.setToolCallId(callId);
    flows.add(Flowable.just(decorateEvent(end)));
    return Flowable.concat(flows);
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

  private Flowable<BaseEvent> mapCorrectionEvent(final Event event) {
    final Map<String, Object> correctionMetadata =
        CorrectionUtils.extractCorrectionMetadata(event);
    if (correctionMetadata.isEmpty()) {
      return Flowable.empty();
    }
    final CustomEvent customEvent = new CustomEvent();
    final BaseEvent decoratedEvent = decorateEvent(customEvent, correctionMetadata);
    LOG.debug("Generated correction event - event={}", JsonUtils.toJson(decoratedEvent));
    return Flowable.just(decoratedEvent);
  }

  private static String resolveDelta(final String text, final String lastSentText) {
    if (StringUtils.isBlank(text)) {
      return "";
    }
    if (StringUtils.isNotBlank(lastSentText) && text.startsWith(lastSentText)) {
      return text.substring(lastSentText.length());
    }
    return text;
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

  private BaseEvent decorateEvent(final BaseEvent event) {
    return decorateEvent(event, null);
  }

  private BaseEvent decorateEvent(final BaseEvent event, final Map<String, Object> extraRaw) {
    event.setTimestamp(System.currentTimeMillis());
    final Map<String, Object> eventMap = JsonUtils.toMap(event);
    final Map<String, Object> rawEvent = CollectionUtils.nullSafeMutableMap(eventMap);
    if (extraRaw != null) {
      rawEvent.putAll(extraRaw);
    }
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
    private String lastSentText;
    private String lastSentThinking;
    private String finalAnswer;
    private boolean isThinking;
    private boolean thinkingMessageOpen;

    private MapperState(final String sessionId, final String agentId) {
      this.sessionId = sessionId;
      this.agentId = agentId;
    }
  }
}

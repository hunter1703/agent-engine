package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agui.core.event.*;
import com.agentengine.interfaces.rest.responses.dtos.*;
import io.reactivex.rxjava3.core.Flowable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps AGUI events to Responses API format for Codex CLI compatibility
 */
public final class ResponsesMapper implements EventMapper<BaseEvent, BaseResponsesEventData> {
  private final MapperState state;

  public ResponsesMapper(final String agentId) {
    this.state = new MapperState(agentId);
  }

  @Override
  public Flowable<BaseResponsesEventData> map(final BaseEvent event) {
    final BaseResponsesEventData mapped = mapEvent(event);
    if (mapped == null) {
      return Flowable.empty();
    }
    return Flowable.just(mapped);
  }

  public Flowable<BaseResponsesEventData> onComplete() {
    return Flowable.defer(this::doneEvent);
  }

  public Flowable<BaseResponsesEventData> onError(final Throwable throwable) {
    return Flowable.defer(() -> {
      final BaseResponsesEventData failed = mapError(throwable);
      return Flowable.concatArray(Flowable.just(failed), doneEvent());
    });
  }

  private BaseResponsesEventData mapEvent(final BaseEvent baseEvent) {
    return switch (baseEvent) {
      case RunStartedEvent runStartedEvent -> mapRunStarted(runStartedEvent);
      case RunFinishedEvent runFinishedEvent -> mapRunFinished(runFinishedEvent);
      case RunErrorEvent runErrorEvent -> mapRunError(runErrorEvent);
      case StepStartedEvent _ -> mapStepStarted();
      case ThinkingStartEvent _ -> mapThinkingStart();
      case ThinkingEndEvent _ -> mapThinkingEnd();
      case TextMessageStartEvent _ -> mapTextMessageStart();
      case TextMessageEndEvent _ -> mapTextMessageEnd();
      case TextMessageChunkEvent textMessageChunkEvent -> mapTextChunk(textMessageChunkEvent);
      case TextMessageContentEvent textMessageContentEvent -> mapTextContent(textMessageContentEvent);
      case ToolCallStartEvent toolCallStartEvent -> mapToolCallStart(toolCallStartEvent);
      case ToolCallArgsEvent toolCallArgsEvent -> mapToolCallArgs(toolCallArgsEvent);
      case ToolCallEndEvent toolCallEndEvent -> mapToolCallEnd(toolCallEndEvent);
      case ToolCallResultEvent toolCallResultEvent -> mapToolCallResult(toolCallResultEvent);
      default -> throw new IllegalStateException(STR."Unexpected value: \{baseEvent}");
    };
  }

  private BaseResponsesEventData mapRunStarted(final RunStartedEvent runStartedEvent) {
    state.responseId = runStartedEvent.getRunId();
    final String agentId = resolveAgentId(runStartedEvent);
    final CreatedEventData createdEventData = new CreatedEventData(state.responseId, agentId);
    state.responseState.clear();
    state.responseState.putAll(createdEventData.getResponse());
    return createdEventData;
  }

  private BaseResponsesEventData mapRunFinished(final RunFinishedEvent runFinishedEvent) {
    if (StringUtils.isBlank(state.responseId)) {
      state.responseId = runFinishedEvent.getRunId();
    }
    updateResponseStatus("completed");
    return new CompletedEventData(runFinishedEvent.getRunId());
  }

  private BaseResponsesEventData mapRunError(final RunErrorEvent runErrorEvent) {
    return buildFailure(runErrorEvent.getError());
  }

  private BaseResponsesEventData mapStepStarted() {
    updateResponseStatus("in_progress");
    return new InProgressEventData();
  }

  private BaseResponsesEventData mapThinkingStart() {
    state.thinkingStarted = true;
    return new ReasoningSummaryPartAddedEventData(state.thinkingIndex);
  }

  private BaseResponsesEventData mapThinkingEnd() {
    state.thinkingIndex++;
    state.thinkingStarted = false;
    return null;
  }

  private BaseResponsesEventData mapTextMessageStart() {
    state.thinkingStarted = false;
    return null;
  }

  private BaseResponsesEventData mapTextMessageEnd() {
    state.thinkingStarted = false;
    state.contentIndex++;
    return null;
  }

  private BaseResponsesEventData mapTextChunk(final TextMessageChunkEvent textMessageChunkEvent) {
    final String delta = textMessageChunkEvent.getDelta();
    if (state.thinkingStarted) {
      return new ReasoningSummaryTextDeltaEventData(delta, state.thinkingIndex);
    }
    return new OutputTextDeltaEventData(delta, state.contentIndex);
  }

  private BaseResponsesEventData mapTextContent(final TextMessageContentEvent textMessageContentEvent) {
    final String delta = textMessageContentEvent.getDelta();
    if (state.thinkingStarted) {
      return new ReasoningSummaryTextDeltaEventData(delta, state.thinkingIndex);
    }
    return new MessageCompletedEventData(delta, state.contentIndex);
  }

  private BaseResponsesEventData mapToolCallStart(final ToolCallStartEvent toolCallStartEvent) {
    state.toolCallDetails.put(toolCallStartEvent.getToolCallId(), new ToolCallDetails(
        toolCallStartEvent.getToolCallName(), new StringBuilder(), state.toolCallIndex++));
    return null;
  }

  private BaseResponsesEventData mapToolCallArgs(final ToolCallArgsEvent toolCallArgsEvent) {
    final String toolCallId = toolCallArgsEvent.getToolCallId();
    final ToolCallDetails details = state.toolCallDetails.get(toolCallId);
    if (details != null) {
      details.argsBuilder().append(toolCallArgsEvent.getDelta());
    }
    return null;
  }

  private BaseResponsesEventData mapToolCallEnd(final ToolCallEndEvent toolCallEndEvent) {
    final String toolCallId = toolCallEndEvent.getToolCallId();
    final ToolCallDetails details = state.toolCallDetails.get(toolCallId);
    if (details == null) {
      return null;
    }
    String arguments = details.argsBuilder().toString();
    arguments = StringUtils.isBlank(arguments) ? "{}" : arguments;

    String toolName = details.name();
    if (StringUtils.isBlank(toolName)) {
      toolName = "unknown";
    }
    return new ToolCallEventData(toolCallId, toolName, arguments, details.index());
  }

  private BaseResponsesEventData mapToolCallResult(final ToolCallResultEvent toolCallResultEvent) {
    final String toolCallId = toolCallResultEvent.getToolCallId();
    final ToolCallDetails details = state.toolCallDetails.get(toolCallId);
    if (details == null) {
      return null;
    }
    state.toolCallDetails.remove(toolCallId);
    return new ToolCallResultEventData(toolCallId, toolCallResultEvent.getContent(), details.index());
  }

  private Flowable<BaseResponsesEventData> doneEvent() {
    final Map<String, Object> response = state.responseState.isEmpty() ? Map.of() : new HashMap<>(state.responseState);
    return Flowable.just(new DoneEventData(response));
  }

  private String resolveAgentId(final BaseEvent event) {
    final String agentId = getValueFromRawEvent(event, "agentId");
    return StringUtils.isNotBlank(agentId) ? agentId : state.agentId;
  }

  private void updateResponseStatus(final String status) {
    if (StringUtils.isBlank(status)) {
      return;
    }
    state.responseState.put("status", status);
    if (StringUtils.isNotBlank(state.responseId)) {
      state.responseState.put("id", state.responseId);
    }
  }

  private BaseResponsesEventData mapError(final Throwable throwable) {
    return buildFailure(throwable.getMessage());
  }

  private BaseResponsesEventData buildFailure(final String error) {
    updateResponseStatus("failed");
    final String id = StringUtils.isNotBlank(state.responseId) ? state.responseId : UUID.randomUUID().toString();
    return new FailedEventData(id, error);
  }

  private static final class MapperState {
    private final Map<String, ToolCallDetails> toolCallDetails = new HashMap<>();
    private final Map<String, Object> responseState = new HashMap<>();
    private final String agentId;
    private int thinkingIndex;
    private int contentIndex;
    private int toolCallIndex;
    private boolean thinkingStarted;
    private String responseId;

    private MapperState(final String agentId) {
      this.agentId = agentId;
    }
  }

  private static <T> T getValueFromRawEvent(final BaseEvent event, final String key) {
    if (event == null) {
      return null;
    }
    //noinspection unchecked
    final Map<String, Object> rawEvent = (Map<String, Object>) event.getRawEvent();
    return CollectionUtils.getValueFromMap(rawEvent, key);
  }

  private record ToolCallDetails(String name, StringBuilder argsBuilder, int index) {
  }
}

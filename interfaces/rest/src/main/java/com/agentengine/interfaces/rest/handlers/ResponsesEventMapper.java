package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agui.core.event.*;
import com.agentengine.interfaces.rest.responses.dtos.*;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps AGUI events to Responses API format for Codex CLI compatibility
 */
public final class ResponsesEventMapper implements EventMapper<BaseEvent, BaseResponsesEventData> {
  private final MapperState state;

  public ResponsesEventMapper(final String agentId) {
    this.state = new MapperState(agentId);
  }

  @Override
  public Flowable<BaseResponsesEventData> map(final BaseEvent event) {
    return mapEvent(event);
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

  private Flowable<BaseResponsesEventData> mapEvent(final BaseEvent baseEvent) {
    return switch (baseEvent) {
      case RunStartedEvent runStartedEvent -> mapRunStarted(runStartedEvent);
      case RunFinishedEvent runFinishedEvent -> mapRunFinished(runFinishedEvent);
      case RunErrorEvent runErrorEvent -> mapRunError(runErrorEvent);
      case StepStartedEvent _ -> mapStepStarted();
      case StepFinishedEvent _ -> Flowable.empty();
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

  private Flowable<BaseResponsesEventData> mapRunStarted(final RunStartedEvent runStartedEvent) {
    state.responseId = runStartedEvent.getRunId();
    final String agentId = resolveAgentId(runStartedEvent);
    final CreatedEventData createdEventData = new CreatedEventData(state.responseId, agentId);
    state.responseState.clear();
    state.responseState.putAll(createdEventData.getResponse());
    return toFlowable(createdEventData);
  }

  private Flowable<BaseResponsesEventData> mapRunFinished(final RunFinishedEvent runFinishedEvent) {
    if (StringUtils.isBlank(state.responseId)) {
      state.responseId = runFinishedEvent.getRunId();
    }
    updateResponseStatus("completed");
    return toFlowable(new CompletedEventData(runFinishedEvent.getRunId()));
  }

  private Flowable<BaseResponsesEventData> mapRunError(final RunErrorEvent runErrorEvent) {
    return toFlowable(buildFailure(runErrorEvent.getError()));
  }

  private Flowable<BaseResponsesEventData> mapStepStarted() {
    updateResponseStatus("in_progress");
    return toFlowable(new InProgressEventData());
  }

  private Flowable<BaseResponsesEventData> mapThinkingStart() {
    if (state.thinkingStarted) {
      return Flowable.empty();
    }
    state.thinkingStarted = true;
    final BaseResponsesEventData reasoningAdded = beginReasoningItem();
    final BaseResponsesEventData summaryPart = new ReasoningSummaryPartAddedEventData(state.thinkingIndex);
    return fromEvents(reasoningAdded, summaryPart);
  }

  private Flowable<BaseResponsesEventData> mapThinkingEnd() {
    if (!state.thinkingStarted) {
      return Flowable.empty();
    }
    final BaseResponsesEventData completed = finishReasoningItem();
    state.thinkingIndex++;
    state.thinkingStarted = false;
    return toFlowable(completed);
  }

  private Flowable<BaseResponsesEventData> mapTextMessageStart() {
    state.thinkingStarted = false;
    return toFlowable(beginMessageItem());
  }

  private Flowable<BaseResponsesEventData> mapTextMessageEnd() {
    state.thinkingStarted = false;
    final BaseResponsesEventData completed = finishMessageItem();
    return toFlowable(completed);
  }

  private Flowable<BaseResponsesEventData> mapTextChunk(final TextMessageChunkEvent textMessageChunkEvent) {
    final String delta = textMessageChunkEvent.getDelta();
    if (state.thinkingStarted) {
      appendReasoning(delta);
      return toFlowable(new ReasoningSummaryTextDeltaEventData(delta, state.thinkingIndex));
    }
    final BaseResponsesEventData messageStart = beginMessageItem();
    appendMessage(delta);
    final BaseResponsesEventData deltaEvent =
        state.activeMessageOutputIndex != null ? new OutputTextDeltaEventData(delta, state.activeMessageOutputIndex)
            : null;
    return fromEvents(messageStart, deltaEvent);
  }

  private Flowable<BaseResponsesEventData> mapTextContent(final TextMessageContentEvent textMessageContentEvent) {
    final String delta = textMessageContentEvent.getDelta();
    if (state.thinkingStarted) {
      appendReasoning(delta);
      return toFlowable(new ReasoningSummaryTextDeltaEventData(delta, state.thinkingIndex));
    }
    final BaseResponsesEventData messageStart = beginMessageItem();
    resetMessage(delta);
    final BaseResponsesEventData completed = finishMessageItem();
    return fromEvents(messageStart, completed);
  }

  private Flowable<BaseResponsesEventData> mapToolCallStart(final ToolCallStartEvent toolCallStartEvent) {
    final int outputIndex = nextOutputIndex();
    state.toolCallDetails.put(toolCallStartEvent.getToolCallId(),
        new ToolCallDetails(toolCallStartEvent.getToolCallName(), new StringBuilder(), outputIndex));
    return toFlowable(
        new ToolCallAddedEventData(toolCallStartEvent.getToolCallId(), toolCallStartEvent.getToolCallName(),
            outputIndex));
  }

  private Flowable<BaseResponsesEventData> mapToolCallArgs(final ToolCallArgsEvent toolCallArgsEvent) {
    final String toolCallId = toolCallArgsEvent.getToolCallId();
    final ToolCallDetails details = state.toolCallDetails.get(toolCallId);
    if (details != null) {
      details.argsBuilder().append(toolCallArgsEvent.getDelta());
    }
    return Flowable.empty();
  }

  private Flowable<BaseResponsesEventData> mapToolCallEnd(final ToolCallEndEvent toolCallEndEvent) {
    final String toolCallId = toolCallEndEvent.getToolCallId();
    final ToolCallDetails details = state.toolCallDetails.get(toolCallId);
    if (details == null) {
      return Flowable.empty();
    }
    String arguments = details.argsBuilder().toString();
    arguments = StringUtils.isBlank(arguments) ? "{}" : arguments;

    String toolName = details.name();
    if (StringUtils.isBlank(toolName)) {
      toolName = "unknown";
    }
    return toFlowable(new ToolCallEventData(toolCallId, toolName, arguments, details.index()));
  }

  private Flowable<BaseResponsesEventData> mapToolCallResult(final ToolCallResultEvent toolCallResultEvent) {
    final String toolCallId = toolCallResultEvent.getToolCallId();
    final ToolCallDetails details = state.toolCallDetails.get(toolCallId);
    if (details == null) {
      return Flowable.empty();
    }
    state.toolCallDetails.remove(toolCallId);
    final int outputIndex = nextOutputIndex();
    return toFlowable(new ToolCallResultEventData(toolCallId, toolCallResultEvent.getContent(), outputIndex));
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

  private BaseResponsesEventData beginMessageItem() {
    if (state.activeMessageOutputIndex != null) {
      return null;
    }
    state.activeMessageOutputIndex = nextOutputIndex();
    state.messageBuffer = new StringBuilder();
    state.messageCompleted = false;
    return new MessageStartedEventData(state.activeMessageOutputIndex);
  }

  private void appendMessage(final String delta) {
    if (state.messageBuffer == null) {
      state.messageBuffer = new StringBuilder();
    }
    state.messageBuffer.append(delta);
  }

  private void resetMessage(final String delta) {
    if (state.messageBuffer == null) {
      state.messageBuffer = new StringBuilder();
    }
    state.messageBuffer.setLength(0);
    state.messageBuffer.append(delta);
  }

  private BaseResponsesEventData finishMessageItem() {
    if (state.activeMessageOutputIndex == null || state.messageCompleted) {
      state.activeMessageOutputIndex = null;
      state.messageBuffer = null;
      state.messageCompleted = false;
      return null;
    }
    final String text = state.messageBuffer != null ? state.messageBuffer.toString() : "";
    final BaseResponsesEventData completed = new MessageCompletedEventData(text, state.activeMessageOutputIndex);
    state.activeMessageOutputIndex = null;
    state.messageBuffer = null;
    state.messageCompleted = true;
    return completed;
  }

  private BaseResponsesEventData beginReasoningItem() {
    if (state.activeReasoningOutputIndex != null) {
      return null;
    }
    state.activeReasoningOutputIndex = nextOutputIndex();
    state.reasoningBuffer = new StringBuilder();
    return new ReasoningAddedEventData(state.activeReasoningOutputIndex);
  }

  private void appendReasoning(final String delta) {
    if (state.reasoningBuffer == null) {
      state.reasoningBuffer = new StringBuilder();
    }
    state.reasoningBuffer.append(delta);
  }

  private BaseResponsesEventData finishReasoningItem() {
    if (state.activeReasoningOutputIndex == null) {
      return null;
    }
    final String summary = state.reasoningBuffer != null ? state.reasoningBuffer.toString() : "";
    final BaseResponsesEventData completed =
        new ReasoningDoneEventData(summary, state.activeReasoningOutputIndex);
    state.activeReasoningOutputIndex = null;
    state.reasoningBuffer = null;
    return completed;
  }

  private int nextOutputIndex() {
    return state.outputIndex++;
  }

  private static Flowable<BaseResponsesEventData> toFlowable(final BaseResponsesEventData event) {
    if (event == null) {
      return Flowable.empty();
    }
    return Flowable.just(event);
  }

  private static Flowable<BaseResponsesEventData> fromEvents(final BaseResponsesEventData... events) {
    if (events == null || events.length == 0) {
      return Flowable.empty();
    }
    final ArrayList<BaseResponsesEventData> collected = new ArrayList<>(events.length);
    for (final BaseResponsesEventData event : events) {
      if (event != null) {
        collected.add(event);
      }
    }
    if (collected.isEmpty()) {
      return Flowable.empty();
    }
    if (collected.size() == 1) {
      return Flowable.just(collected.getFirst());
    }
    return Flowable.fromIterable(collected);
  }

  private static final class MapperState {
    private final Map<String, ToolCallDetails> toolCallDetails = new HashMap<>();
    private final Map<String, Object> responseState = new HashMap<>();
    private final String agentId;
    private int thinkingIndex;
    private int outputIndex;
    private boolean thinkingStarted;
    private String responseId;
    private Integer activeMessageOutputIndex;
    private Integer activeReasoningOutputIndex;
    private StringBuilder messageBuffer;
    private StringBuilder reasoningBuffer;
    private boolean messageCompleted;

    private MapperState(final String agentId) {
      this.agentId = agentId;
    }
  }

  private static <T> T getValueFromRawEvent(final BaseEvent event, final String key) {
    if (event == null) {
      return null;
    }
    // noinspection unchecked
    final Map<String, Object> rawEvent = (Map<String, Object>) event.getRawEvent();
    return CollectionUtils.getValueFromMap(rawEvent, key);
  }

  private record ToolCallDetails(String name, StringBuilder argsBuilder, int index) {
  }
}

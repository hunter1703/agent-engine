package com.agentengine.interfaces.rest.handlers;

import static com.google.adk.sessions.State.REMOVED;
import static com.google.adk.sessions.State.TEMP_PREFIX;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.ExceptionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agui.core.event.*;
import com.agui.core.message.AssistantMessage;
import com.agui.core.message.BaseMessage;
import com.agui.core.message.Role;
import com.agui.core.message.SystemMessage;
import com.agui.core.message.UserMessage;
import com.agui.core.state.State;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

public final class AGUIEventEmitter {
  private static final Logger LOGGER = LoggerFactory.getLogger(AGUIEventEmitter.class);
  private final String threadId;
  private final Consumer<? super BaseEvent> eventConsumer;
  private String runId;
  private String currentStepName;
  private String currentTextMessageId;
  private boolean thinkingStarted;
  private final Set<String> pendingToolCalls = new HashSet<>();

  public AGUIEventEmitter(final String threadId, final Consumer<? super BaseEvent> eventConsumer) {
    this.threadId = threadId;
    this.eventConsumer = eventConsumer;
  }

  public void onComplete() {
    if (runId == null) {
      return;
    }
    emitRunFinished(runId);
  }

  private void emitRunFinished(final String runId) {
    final RunFinishedEvent event = new RunFinishedEvent();
    event.setThreadId(threadId);
    event.setRunId(runId);
    emit(event);
  }

  public void onError(final Throwable throwable) {
    if (runId == null) {
      runId = "unknown";
    }
    final RunErrorEvent event = new RunErrorEvent();
    event.setError(ExceptionUtils.getErrorMessage(throwable));
    emit(event);
  }

  public void onEvent(final Event event) {
    if (isEmptyEvent(event)) {
      LOGGER.info("Skipping empty event: {}", event.id());
      return;
    }
    Functions.populateClientFunctionCallId(event);

    startRun(event);
    startStep();
    emitText(event);
    emitFunctionCalls(event.functionCalls());
    emitFunctionResponses(event.functionResponses());
    finishStep(event);
  }

  private void startRun(final Event event) {
    if (hasRunStarted()) {
      LOGGER.warn("A run is already started with id: {}", runId);
      return;
    }
    runId = event.invocationId();
    final RunStartedEvent runStartedEvent = new RunStartedEvent();
    runStartedEvent.setThreadId(threadId);
    runStartedEvent.setRunId(runId);
    emit(runStartedEvent);
  }

  private boolean hasRunStarted() {
    return StringUtils.isNotBlank(runId);
  }

  private void emitText(final Event event) {
    final Content content = event.content().orElse(null);
    if (content == null) {
      return;
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

    thinking(thoughtBuilder.toString(), partial);
    answer(answerBuilder.toString(), partial);
  }

  private void thinking(final String text, final boolean partial) {
    startThinking();
    emitMessage(null, text, partial);
    finishThinking(partial);
  }

  private void startThinking() {
    if (thinkingStarted) {
      return;
    }
    emit(new ThinkingStartEvent());
    thinkingStarted = true;
  }

  private void emitMessage(final String messageId, final String text, final boolean partial) {
    if (partial) {
      final TextMessageChunkEvent chunkEvent = new TextMessageChunkEvent();
      chunkEvent.setMessageId(messageId);
      chunkEvent.setDelta(text);
      chunkEvent.setRole("assistant");
      emit(chunkEvent);
    } else {
      final TextMessageContentEvent event = new TextMessageContentEvent();
      event.setMessageId(messageId);
      event.setDelta(text);
      emit(event);
    }
  }

  private void finishThinking(final boolean partial) {
    if (partial || !thinkingStarted) {
      return;
    }
    emit(new ThinkingEndEvent());
    thinkingStarted = false;
  }

  private void answer(final String text, final boolean partial) {
    startAnswer();
    emitMessage(currentTextMessageId, text, partial);
    finishAnswer(partial);
  }

  private void startAnswer() {
        if (hasAnswerStarted()) {
            return;
        }
        currentTextMessageId = STR."msg-\{UUID.randomUUID().toString()}";
        final TextMessageStartEvent event = new TextMessageStartEvent();
        event.setMessageId(currentTextMessageId);
        event.setRole("assistant");
        emit(event);
    }

  private boolean hasAnswerStarted() {
    return StringUtils.isNotBlank(currentTextMessageId);
  }

  private void finishAnswer(final boolean partial) {
    if (partial || !hasAnswerStarted()) {
      return;
    }
    final TextMessageEndEvent event = new TextMessageEndEvent();
    event.setMessageId(currentTextMessageId);
    emit(event);
    currentTextMessageId = null;
  }

  private void emitFunctionCalls(final List<FunctionCall> calls) {
    for (final FunctionCall call : CollectionUtils.nullSafeList(calls)) {
      final String toolCallId = call.id().orElseThrow();
      final String name = call.name().orElse("tool");

      pendingToolCalls.add(toolCallId);

      startToolCall(toolCallId, name);
      toolCall(call, toolCallId);
      finishToolCall(toolCallId);
    }
  }

  private void finishToolCall(final String toolCallId) {
    final ToolCallEndEvent end = new ToolCallEndEvent();
    end.setToolCallId(toolCallId);
    emit(end);
  }

  private void toolCall(final FunctionCall call, final String toolCallId) {
    final Map<String, Object> args = call.args().orElse(Map.of());
    if (CollectionUtils.isNotEmpty(args)) {
      final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
      argsEvent.setToolCallId(toolCallId);
      argsEvent.setDelta(JsonUtils.toJson(args));
      emit(argsEvent);
    }
  }

  private void startToolCall(final String toolCallId, final String toolCallName) {
    final ToolCallStartEvent start = new ToolCallStartEvent();
    start.setToolCallId(toolCallId);
    start.setToolCallName(toolCallName);
    emit(start);
  }

  private void emitFunctionResponses(final List<FunctionResponse> responses) {
    for (final FunctionResponse response : CollectionUtils.nullSafeList(responses)) {
      final String toolCallId = response.id().orElseThrow();
      final Map<String, Object> payload = response.response().orElse(Map.of());

      toolResult(toolCallId, payload);

      pendingToolCalls.remove(toolCallId);
    }
  }

  private void toolResult(final String toolCallId, final Map<String, Object> payload) {
    final ToolCallResultEvent event = new ToolCallResultEvent();
    event.setToolCallId(toolCallId);
    event.setMessageId(toolCallId);
    event.setRole(Role.tool);
    event.setContent(JsonUtils.toJson(payload));
    emit(event);
  }

  private void emit(final BaseEvent event) {
    if (eventConsumer == null) {
      return;
    }
    event.setTimestamp(System.currentTimeMillis());
    eventConsumer.accept(event);
  }

  private void startStep() {
        if (hasStepStarted()) {
            return;
        }
        currentStepName = STR."step-\{UUID.randomUUID().toString()}";
        final StepStartedEvent stepEvent = new StepStartedEvent();
        stepEvent.setStepName(currentStepName);
        emit(stepEvent);
    }

  private boolean hasStepStarted() {
    return StringUtils.isNotBlank(currentStepName);
  }

  private void finishStep(final Event event) {
    if (!hasStepStarted() || CollectionUtils.isNotEmpty(pendingToolCalls) || event.partial().orElse(false)) {
      return;
    }
    final Content content = event.content().orElse(null);
    boolean stepFinished = true;
    if (content != null) {
      stepFinished = content.parts().orElse(List.of()).stream().anyMatch(part -> !part.thought().orElse(false));
    }
    if (stepFinished) {
      final StepFinishedEvent stepEvent = new StepFinishedEvent();
      stepEvent.setStepName(currentStepName);
      emit(stepEvent);
      currentStepName = null;
    }
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
    for (Part part : content.parts().orElse(List.of())) {
      if (StringUtils.isNotBlank(part.text().orElse(null))) {
        return false;
      }
    }
    return true;
  }
}

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class AGUIEventEmitter {
  private final String threadId;
  private final Consumer<? super BaseEvent> eventConsumer;
  private final Map<String, String> toolCallNames = new LinkedHashMap<>();
  private final List<BaseMessage> messageSnapshot = new ArrayList<>();
  private final Map<String, Object> stateSnapshot = new LinkedHashMap<>();
  private String finalAnswer;
  private String finalThoughts;
  private String runId;
  private int messageCounter;
  private int stepCounter;
  private boolean stepOpen;
  private String currentStepName;
  private String currentTextMessageId;
  private String currentTextRole;
  private StringBuilder currentTextContent;
  private String currentThoughtMessageId;
  private StringBuilder thoughtMessage;
  private boolean thinkingOpen;

  public AGUIEventEmitter(final String threadId, final Consumer<? super BaseEvent> eventConsumer) {
    this.threadId = threadId;
    this.eventConsumer = eventConsumer;
  }

  public void onEvent(final Event event) {
    if (event == null) {
      return;
    }
    if (runId == null) {
      runId = event.invocationId();
      emitRunStarted(runId);
    }
    emitStepStartedIfNeeded(event);
    Functions.populateClientFunctionCallId(event);
    emitTextParts(event);
    emitFunctionCalls(event.functionCalls());
    emitFunctionResponses(event.functionResponses());
    emitStateEvents(event);
    emitStepFinishedIfNeeded(event);
  }

  public void onComplete() {
    if (runId == null) {
      return;
    }
    emitRunFinished(runId);
  }

  public void onError(final Throwable throwable) {
    if (runId == null) {
      runId = "unknown";
    }
    final RunErrorEvent event = new RunErrorEvent();
    event.setError(ExceptionUtils.getErrorMessage(throwable));
    emit(event);
  }

  public String getFinalAnswer() {
    return finalAnswer;
  }

  public String getFinalThoughts() {
    return finalThoughts;
  }

  private void emitRunStarted(final String runId) {
    final RunStartedEvent event = new RunStartedEvent();
    event.setThreadId(threadId);
    event.setRunId(runId);
    emit(event);
  }

  private void emitRunFinished(final String runId) {
    final RunFinishedEvent event = new RunFinishedEvent();
    event.setThreadId(threadId);
    event.setRunId(runId);
    if (finalAnswer != null) {
      event.setResult(finalAnswer);
    }
    emit(event);
  }

  private void emitTextParts(final Event event) {
    final Content content = event.content().orElse(null);
    if (content == null) {
      return;
    }
    final boolean partial = event.partial().orElse(false);
    final String role = roleFor(event.author());
    final List<Part> parts = content.parts().orElse(List.of());
    for (final Part part : parts) {
      final String text = part.text().orElse(null);
      if (StringUtils.isBlank(text)) {
        continue;
      }
      if (part.thought().orElse(false)) {
        emitThinkingText(role, text);
      } else {
        emitText(role, text);
      }
    }
    if (!partial) {
      finishTextMessage();
      finishThinkingMessage();
    }
  }

  private void emitText(final String role, final String text) {
    final String messageId = ensureTextMessage(role);
    final TextMessageContentEvent event = new TextMessageContentEvent();
    event.setMessageId(messageId);
    event.setDelta(text);
    emit(event);
    final TextMessageChunkEvent chunkEvent = new TextMessageChunkEvent();
    chunkEvent.setMessageId(messageId);
    chunkEvent.setRole(role);
    chunkEvent.setDelta(text);
    emit(chunkEvent);
    if (currentTextContent != null) {
      currentTextContent.append(text);
    }
  }

  private void emitThinkingText(final String role, final String text) {
    final String messageId = ensureThinkingMessage(role);
    final ThinkingTextMessageContentEvent event = new ThinkingTextMessageContentEvent();
    event.setRawEvent(Map.of("messageId", messageId, "delta", text));
    emit(event);
    thoughtMessage.append(text);
  }

  private String ensureTextMessage(final String role) {
    if (currentTextMessageId == null || !Objects.equals(currentTextRole, role)) {
      finishTextMessage();
      currentTextMessageId = "msg-" + (++messageCounter);
      currentTextRole = role;
      final TextMessageStartEvent event = new TextMessageStartEvent();
      event.setMessageId(currentTextMessageId);
      event.setRole(role);
      emit(event);
      currentTextContent = new StringBuilder();
    }
    return currentTextMessageId;
  }

  private String ensureThinkingMessage(final String role) {
    if (currentThoughtMessageId == null) {
      currentThoughtMessageId = "think-" + (++messageCounter);
      if (!thinkingOpen) {
        emit(new ThinkingStartEvent());
        thinkingOpen = true;
      }
      final ThinkingTextMessageStartEvent event = new ThinkingTextMessageStartEvent();
      event.setRawEvent(Map.of("messageId", currentThoughtMessageId, "role", role));
      emit(event);
      thoughtMessage = new StringBuilder();
    }
    return currentThoughtMessageId;
  }

  private void finishTextMessage() {
    if (currentTextMessageId == null) {
      return;
    }
    final TextMessageEndEvent event = new TextMessageEndEvent();
    event.setMessageId(currentTextMessageId);
    emit(event);
    final String content = currentTextContent == null ? "" : currentTextContent.toString();
    if ("assistant".equalsIgnoreCase(currentTextRole)) {
      finalAnswer = content;
    }
    if (StringUtils.isNotBlank(currentTextRole)) {
      messageSnapshot.add(buildMessage(currentTextMessageId, currentTextRole, content));
      emitMessagesSnapshot();
    }
    currentTextContent = null;
    currentTextMessageId = null;
    currentTextRole = null;
  }

  private void finishThinkingMessage() {
    if (currentThoughtMessageId == null) {
      return;
    }
    final ThinkingTextMessageEndEvent event = new ThinkingTextMessageEndEvent();
    event.setRawEvent(Map.of("messageId", currentThoughtMessageId));
    emit(event);
    if (thoughtMessage != null && thoughtMessage.length() > 0) {
      finalThoughts = thoughtMessage.toString();
    }
    thoughtMessage = null;
    currentThoughtMessageId = null;
    if (thinkingOpen) {
      emit(new ThinkingEndEvent());
      thinkingOpen = false;
    }
  }

  private void emitFunctionCalls(final List<FunctionCall> calls) {
    for (final FunctionCall call : calls) {
      final String toolCallId = call.id().orElse("call-" + (++messageCounter));
      final String name = call.name().orElse("");
      toolCallNames.put(toolCallId, name);
      final ToolCallStartEvent start = new ToolCallStartEvent();
      start.setToolCallId(toolCallId);
      start.setToolCallName(name);
      if (currentTextMessageId != null) {
        start.setParentMessageId(currentTextMessageId);
      }
      emit(start);

      final Map<String, Object> args = call.args().orElse(Map.of());
      if (!args.isEmpty()) {
        final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
        argsEvent.setToolCallId(toolCallId);
        argsEvent.setDelta(JsonUtils.toJson(args));
        emit(argsEvent);
        final ToolCallChunkEvent chunkEvent = new ToolCallChunkEvent();
        chunkEvent.setToolCallId(toolCallId);
        chunkEvent.setToolCallName(name);
        if (currentTextMessageId != null) {
          chunkEvent.setParentMessageId(currentTextMessageId);
        }
        chunkEvent.setDelta(JsonUtils.toJson(args));
        emit(chunkEvent);
      }

      final ToolCallEndEvent end = new ToolCallEndEvent();
      end.setToolCallId(toolCallId);
      emit(end);
    }
  }

  private void emitFunctionResponses(final List<FunctionResponse> responses) {
    for (final FunctionResponse response : responses) {
      final String toolCallId = response.id().orElse("call-" + (++messageCounter));
      final Map<String, Object> payload = response.response().orElse(Map.of());
      final Object output = payload.get("output");
      final String content = output == null ? JsonUtils.toJson(payload) : output.toString();
      final ToolCallResultEvent event = new ToolCallResultEvent();
      event.setToolCallId(toolCallId);
      event.setMessageId(toolCallId);
      event.setRole(Role.tool);
      event.setContent(content);
      emit(event);
    }
  }

  private void emitStateEvents(final Event event) {
    if (event == null) {
      return;
    }
    final EventActions actions = event.actions();
    if (actions == null || CollectionUtils.isEmpty(actions.stateDelta())) {
      return;
    }
    final Map<String, Object> stateDelta = actions.stateDelta();
    applyStateDelta(stateDelta);
    final StateDeltaEvent deltaEvent = new StateDeltaEvent();
    deltaEvent.setRawEvent(stateDelta);
    emit(deltaEvent);

    final StateSnapshotEvent snapshotEvent = new StateSnapshotEvent();
    snapshotEvent.setState(new State(new LinkedHashMap<>(stateSnapshot)));
    emit(snapshotEvent);
  }

  private void applyStateDelta(final Map<String, Object> stateDelta) {
    if (CollectionUtils.isEmpty(stateDelta)) {
      return;
    }
    for (Map.Entry<String, Object> entry : stateDelta.entrySet()) {
      final String key = entry.getKey();
      if (StringUtils.isBlank(key) || key.startsWith(TEMP_PREFIX)) {
        continue;
      }
      final Object value = entry.getValue();
      if (value == REMOVED) {
        stateSnapshot.remove(key);
      } else {
        stateSnapshot.put(key, value);
      }
    }
  }

  private void emit(final BaseEvent event) {
    if (eventConsumer == null) {
      return;
    }
    event.setTimestamp(System.currentTimeMillis());
    eventConsumer.accept(event);
  }

  private static String roleFor(final String author) {
    if (author == null) {
      return "assistant";
    }
    if ("user".equalsIgnoreCase(author)) {
      return "user";
    }
    return "assistant";
  }

  private void emitStepStartedIfNeeded(final Event event) {
    if (!isStepEvent(event)) {
      return;
    }
    if (!stepOpen) {
      currentStepName = "step-" + (++stepCounter);
      final StepStartedEvent stepEvent = new StepStartedEvent();
      stepEvent.setStepName(currentStepName);
      emit(stepEvent);
      stepOpen = true;
    }
  }

  private void emitStepFinishedIfNeeded(final Event event) {
    if (!stepOpen || !isStepEvent(event)) {
      return;
    }
    final boolean partial = event.partial().orElse(false);
    if (partial) {
      return;
    }
    final StepFinishedEvent stepEvent = new StepFinishedEvent();
    stepEvent.setStepName(currentStepName);
    emit(stepEvent);
    stepOpen = false;
    currentStepName = null;
  }

  private boolean isStepEvent(final Event event) {
    if (event == null) {
      return false;
    }
    if (CollectionUtils.isNotEmpty(event.functionCalls())) {
      return true;
    }
    final Content content = event.content().orElse(null);
    if (content == null) {
      return false;
    }
    for (Part part : content.parts().orElse(List.of())) {
      if (StringUtils.isNotBlank(part.text().orElse(null))) {
        return true;
      }
    }
    return false;
  }

  private void emitMessagesSnapshot() {
    if (messageSnapshot.isEmpty()) {
      return;
    }
    final MessagesSnapshotEvent event = new MessagesSnapshotEvent();
    event.setMessages(new ArrayList<>(messageSnapshot));
    emit(event);
  }

  private static BaseMessage buildMessage(final String messageId, final String role, final String content) {
    final BaseMessage message;
    if ("user".equalsIgnoreCase(role)) {
      message = new UserMessage();
    } else if ("assistant".equalsIgnoreCase(role)) {
      message = new AssistantMessage();
    } else {
      message = new SystemMessage();
    }
    message.setId(messageId);
    message.setContent(content);
    return message;
  }
}

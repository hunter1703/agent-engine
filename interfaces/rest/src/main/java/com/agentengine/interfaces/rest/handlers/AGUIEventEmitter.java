package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentListener;
import com.agui.core.event.*;
import com.agui.core.message.Role;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class AGUIEventEmitter implements AgentListener {
  private final String threadId;
  private final String inputMessage;
  private final Consumer<? super BaseEvent> eventConsumer;
  private String finalAnswer;
  private String assistantMessageId;
  private StringBuilder assistantMessage;

  public AGUIEventEmitter(final String threadId, final String inputMessage,
      final Consumer<? super BaseEvent> eventConsumer) {
    this.threadId = threadId;
    this.inputMessage = inputMessage;
    this.eventConsumer = eventConsumer;
  }

  @Override
  public void onRunStarted(final String sessionId, final String runId) {
    final RunStartedEvent event = new RunStartedEvent();
    event.setThreadId(threadId);
    event.setRunId(runId);
    event.setRawEvent(Map.of("input", Map.of("message", inputMessage)));
    emit(event);
  }

  @Override
  public void onRunFinished(final String sessionId, final String runId) {
    final RunFinishedEvent event = new RunFinishedEvent();
    event.setThreadId(threadId);
    event.setRunId(runId);
    if (finalAnswer != null) {
      event.setResult(finalAnswer);
    }
    emit(event);
  }

  @Override
  public void onRunError(final String sessionId, final String runId, final Throwable t) {
    final RunErrorEvent event = new RunErrorEvent();
    event.setError(t == null ? null : t.getMessage());
    emit(event);
  }

  @Override
  public void onStepStarted(final String sessionId, final String stepName) {
    final StepStartedEvent event = new StepStartedEvent();
    event.setStepName(stepName);
    emit(event);
  }

  @Override
  public void onStepFinished(final String sessionId, final String stepName) {
    final StepFinishedEvent event = new StepFinishedEvent();
    event.setStepName(stepName);
    emit(event);
  }

  @Override
  public void onTextMessageStart(final String sessionId, final String messageId, final String role) {
    final TextMessageStartEvent event = new TextMessageStartEvent();
    event.setMessageId(messageId);
    event.setRole(role);
    emit(event);
    if ("assistant".equalsIgnoreCase(role)) {
      assistantMessageId = messageId;
      assistantMessage = new StringBuilder();
    } else {
      assistantMessageId = null;
      assistantMessage = null;
    }
  }

  @Override
  public void onTextMessageDelta(final String sessionId, final String messageId, final String delta) {
    final TextMessageContentEvent event = new TextMessageContentEvent();
    event.setMessageId(messageId);
    event.setDelta(delta);
    emit(event);
    if (Objects.equals(messageId, assistantMessageId) && assistantMessage != null && delta != null) {
      assistantMessage.append(delta);
    }
  }

  @Override
  public void onTextMessageEnd(final String sessionId, final String messageId) {
    final TextMessageEndEvent event = new TextMessageEndEvent();
    event.setMessageId(messageId);
    emit(event);
    if (Objects.equals(messageId, assistantMessageId) && assistantMessage != null) {
      finalAnswer = assistantMessage.toString();
      assistantMessage = null;
      assistantMessageId = null;
    }
  }

  @Override
  public void onToolCallStart(final String sessionId, final String toolCallId, final String toolCallName) {
    final ToolCallStartEvent event = new ToolCallStartEvent();
    event.setToolCallId(toolCallId);
    event.setToolCallName(toolCallName);
    emit(event);
  }

  @Override
  public void onToolCallArgs(final String sessionId, final String toolCallId, final String delta) {
    final ToolCallArgsEvent event = new ToolCallArgsEvent();
    event.setToolCallId(toolCallId);
    event.setDelta(delta);
    emit(event);
  }

  @Override
  public void onToolCallEnd(final String sessionId, final String toolCallId) {
    final ToolCallEndEvent event = new ToolCallEndEvent();
    event.setToolCallId(toolCallId);
    emit(event);
  }

  @Override
  public void onToolCallResult(final String sessionId, final String toolCallId, final String content) {
    final ToolCallResultEvent event = new ToolCallResultEvent();
    event.setToolCallId(toolCallId);
    event.setMessageId(toolCallId);
    event.setRole(Role.tool);
    event.setContent(content);
    emit(event);
  }

  @Override
  public void onThinkingMessageStart(final String sessionId, final String messageId, final String role) {
    final ThinkingTextMessageStartEvent event = new ThinkingTextMessageStartEvent();
    event.setRawEvent(Map.of("messageId", messageId, "role", role));
    emit(event);
  }

  @Override
  public void onThinkingMessageDelta(final String sessionId, final String messageId, final String delta) {
    final ThinkingTextMessageContentEvent event = new ThinkingTextMessageContentEvent();
    event.setRawEvent(Map.of("messageId", messageId, "delta", delta));
    emit(event);
  }

  @Override
  public void onThinkingMessageEnd(final String sessionId, final String messageId) {
    final ThinkingTextMessageEndEvent event = new ThinkingTextMessageEndEvent();
    event.setRawEvent(Map.of("messageId", messageId));
    emit(event);
  }

  private void emit(final BaseEvent event) {
    event.setTimestamp(System.currentTimeMillis());
    eventConsumer.accept(event);
  }
}

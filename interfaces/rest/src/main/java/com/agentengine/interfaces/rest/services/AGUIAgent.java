package com.agentengine.interfaces.rest.services;

import com.agentengine.commons.utils.JsonUtils;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.beans.session.ToolRequest;
import com.agentengine.interfaces.rest.services.beans.ToolPlanEvent;
import com.agui.core.agent.AgentSubscriber;
import com.agui.core.event.*;
import com.agui.core.exception.AGUIException;
import com.agui.core.function.FunctionCall;
import com.agui.core.message.AssistantMessage;

import java.util.Collection;
import java.util.List;

public class AGUIAgent implements Agent {

  private final Agent agentEngine;

  public AGUIAgent(final Agent agentEngine) throws AGUIException {
    this.agentEngine = agentEngine;
  }

  public void run(final String sessionId, final String message, final AgentSubscriber agentSubscriber) {
    agentEngine.invoke(sessionId, Message.user(message), new AgentListener() {
      @Override
      public void onRunStarted(String sessionId, String runId) {
        RunStartedEvent event = new RunStartedEvent();
        event.setRunId(runId);
        agentSubscriber.onRunStartedEvent(event);
      }

      @Override
      public void onRunFinished(String sessionId, String runId) {
        RunFinishedEvent event = new RunFinishedEvent();
        event.setRunId(runId);
        agentSubscriber.onRunFinishedEvent(event);
      }

      @Override
      public void onStepStarted(String sessionId, String stepName) {
        StepStartedEvent event = new StepStartedEvent();
        event.setStepName(stepName);
        agentSubscriber.onStepStartedEvent(event);
      }

      @Override
      public void onStepFinished(String sessionId, String stepName) {
        StepFinishedEvent event = new StepFinishedEvent();
        event.setStepName(stepName);
        agentSubscriber.onStepFinishedEvent(event);
      }

      @Override
      public void onTextMessageStart(String sessionId, String messageId, String role) {
        TextMessageStartEvent event = new TextMessageStartEvent();
        event.setMessageId(messageId);
        event.setRole(role);
        agentSubscriber.onTextMessageStartEvent(event);
      }

      @Override
      public void onTextMessageDelta(String sessionId, String messageId, String delta) {
        TextMessageContentEvent event = new TextMessageContentEvent();
        event.setMessageId(messageId);
        event.setDelta(delta);
        agentSubscriber.onTextMessageContentEvent(event);
      }

      @Override
      public void onTextMessageEnd(String sessionId, String messageId) {
        TextMessageEndEvent event = new TextMessageEndEvent();
        event.setMessageId(messageId);
        agentSubscriber.onTextMessageEndEvent(event);
      }

      @Override
      public void onToolCallStart(String sessionId, String toolCallId, String toolCallName) {
        ToolCallStartEvent event = new ToolCallStartEvent();
        event.setToolCallId(toolCallId);
        event.setToolCallName(toolCallName);
        agentSubscriber.onToolCallStartEvent(event);
      }

      @Override
      public void onToolCallArgs(String sessionId, String toolCallId, String delta) {
        ToolCallArgsEvent event = new ToolCallArgsEvent();
        event.setToolCallId(toolCallId);
        event.setDelta(delta);
        agentSubscriber.onToolCallArgsEvent(event);
      }

      @Override
      public void onToolCallEnd(String sessionId, String toolCallId) {
        ToolCallEndEvent event = new ToolCallEndEvent();
        event.setToolCallId(toolCallId);
        agentSubscriber.onToolCallEndEvent(event);
      }

      @Override
      public void onToolCallResult(String sessionId, String toolCallId, String content) {
        ToolCallResultEvent event = new ToolCallResultEvent();
        event.setToolCallId(toolCallId);
        event.setContent(content);
        agentSubscriber.onToolCallResultEvent(event);
      }

      @Override
      public void onReasoningMessageStart(String sessionId, String messageId, String role) {
        ThinkingTextMessageStartEvent event = new ThinkingTextMessageStartEvent();
        agentSubscriber.onEvent(event);
      }

      @Override
      public void onReasoningMessageDelta(String sessionId, String messageId, String delta) {
        ThinkingTextMessageContentEvent event = new ThinkingTextMessageContentEvent();
        agentSubscriber.onEvent(event);
      }

      @Override
      public void onReasoningMessageEnd(String sessionId, String messageId) {
        ThinkingTextMessageEndEvent event = new ThinkingTextMessageEndEvent();
        agentSubscriber.onEvent(event);
      }

      @Override
      public void onToolPlan(String sessionId, Collection<ToolCall> toolCalls) {
        agentSubscriber.onCustomEvent(new ToolPlanEvent(toolCalls));
      }

      @Override
      public void onFinalAnswer(final String sessionId, final Message message) {
        AssistantMessage baseMessage = new AssistantMessage();
        baseMessage.setContent(message.getContent());
        agentSubscriber.onNewMessage(baseMessage);
      }
    });
  }

  @Override
  public Message invoke(final String sessionId, final Message message, final AgentListener listener) {
    return agentEngine.invoke(sessionId, message, listener);
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    return agentEngine.buildPrompt(sessionId);
  }
}

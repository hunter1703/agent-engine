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
      public void onToolPlan(String sessionId, Collection<ToolCall> toolCalls) {
        agentSubscriber.onCustomEvent(new ToolPlanEvent(toolCalls));
      }

      @Override
      public void onToolExecution(String sessionId, ToolExecution toolExecution) {
        // Convert ToolExecution to ToolCall and notify subscriber
        ToolCall toolCall = toolExecution.getToolCall();
        if (toolCall != null) {
          agentSubscriber.onNewToolCall(new com.agui.core.tool.ToolCall(toolCall.id(), "TOOL",
              new FunctionCall(toolCall.name(), JsonUtils.toJson(toolCall.args()))));
        }
      }

      @Override
      public void onReasoningStart(String sessionId) {
      }

      @Override
      public void onReasoningEnd(String sessionId, Message message) {
      }

      @Override
      public void onToolRepair(String sessionId, List<ToolCall> toolCalls, List<ToolRequest> remainingRequests) {
      }

      @Override
      public void onFinalAnswer(final String sessionId, final Message message) {
        // Convert the message to a BaseMessage and notify subscriber
        // Since we can't directly instantiate UserMessage with content, we'll create it
        // and set content
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

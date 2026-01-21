package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolRequest;

import java.util.Collection;
import java.util.List;

public interface AgentListener extends EngineListener {
  default void onToolPlan(String sessionId, Collection<ToolCall> toolCalls) {
  }

  default void onToolExecution(String sessionId, ToolExecution toolExecution) {
  }

  default void onReasoningStart(String sessionId) {
  }

  default void onReasoningEnd(String sessionId, Message message) {
  }

  default void onToolRepair(String sessionId, List<ToolCall> toolCalls, List<ToolRequest> remainingRequests) {
  }

  void onFinalAnswer(String sessionId, Message message);
}

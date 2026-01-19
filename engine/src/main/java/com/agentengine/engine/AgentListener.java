package com.agentengine.engine;

import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.ToolCall;
import com.agentengine.engine.utils.ToolRequest;

import java.util.Collection;
import java.util.List;

public interface AgentListener {
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

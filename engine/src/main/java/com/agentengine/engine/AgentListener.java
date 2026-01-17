package com.agentengine.engine;

import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.ToolCall;
import java.util.List;

public interface AgentListener {
  default void onToolPlan(String sessionId, List<ToolCall> toolCalls) {}

  default void onToolExecution(String sessionId, ToolExecution toolExecution) {}

  default void onReasoningStart(String sessionId) {}

  default void onReasoningEnd(String sessionId) {}

  default void onToolRepair(String sessionId) {}

  void onFinalAnswer(String sessionId, Message message);
}

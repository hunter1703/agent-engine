package com.agentengine.engine;

import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.message.ToolCall;
import java.util.List;

public interface AgentListener {
  default void onToolPlan(final String sessionId, final List<ToolCall> toolCalls) {}

  default void onToolExecution(final String sessionId, final ToolExecution toolExecution) {}

  default void onReasoningStart(final String sessionId) {}

  default void onReasoningEnd(final String sessionId) {}

  default void onToolRepair(final String sessionId) {}
}


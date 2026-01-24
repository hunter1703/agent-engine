package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;

import java.util.List;

public interface ToolExecutor {
  List<ToolExecution> execute(final String sessionId, final String runId, final List<ToolCall> toolCalls,
      final AgentListener listener);
}

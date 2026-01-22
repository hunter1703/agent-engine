package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;

import java.util.List;

public interface AgenticToolExecutor extends ToolExecutor {

  List<ToolExecution> executeRequests(final String sessionId, final List<String> toolRequests,
      final AgentListener listener);
}

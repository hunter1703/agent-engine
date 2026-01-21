package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;

import java.util.List;

public interface ToolExecutor {
    List<ToolExecution> execute(final String sessionId, final List<ToolCall> toolCalls, final AgentListener listener);
}

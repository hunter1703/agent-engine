package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.ToolCall;

public interface ToolHandler {
  String name();

  boolean isMutating();

  boolean supportsParallel();

  ToolResult handle(ToolCall toolCall, ToolContext context);
}

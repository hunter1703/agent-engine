package com.agentengine.engine.api;

import com.google.adk.tools.BaseTool;

import java.util.Map;

public interface ToolProvider {
  String agentId();

  String toolName();

  BaseTool create(AgentContext agentContext, Map<String, Object> toolConfig);
}

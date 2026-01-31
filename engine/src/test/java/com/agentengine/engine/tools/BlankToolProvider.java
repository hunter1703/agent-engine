package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.ToolProvider;
import com.google.adk.tools.BaseTool;

import java.util.Map;

public class BlankToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "test-agent";
  }

  @Override
  public String toolName() {
    return "";
  }

  @Override
  public BaseTool create(final AgentContext agentContext, final Map<String, Object> toolConfig) {
    return null;
  }
}

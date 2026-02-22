package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.google.adk.tools.BaseTool;
import java.util.List;
import java.util.Map;

public class BlankToolProvider implements ToolProvider {
  @Override
  public List<ToolDescriptor> tools() {
    return List.of();
  }

  @Override
  public BaseTool create(
      final AgentContext agentContext,
      final String toolName,
      final Map<String, Object> toolConfig) {
    return null;
  }
}

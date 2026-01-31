package com.agentengine.plugins.echoagent.tools;

import com.agentengine.engine.api.ToolProvider;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;

import java.util.Map;

public final class EchoToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "echo_agent";
  }

  @Override
  public String toolName() {
    return "echo";
  }

  @Override
  public BaseTool create(final Map<String, Object> toolConfig) {
    final String prefix = CollectionUtils.getStringValueFromMap(toolConfig, "prefix");
    return FunctionTool.create(new EchoTool(prefix), "echo");
  }
}

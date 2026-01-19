package com.agentengine.plugins.echoagent.tools;

import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolProvider;
import java.util.Map;

public final class EchoToolProvider implements ToolProvider {
  @Override
  public String agentName() {
    return "echo_agent";
  }

  @Override
  public String toolName() {
    return "echo";
  }

  @Override
  public Tool create(final Map<String, Object> toolConfig) {
    return new EchoTool();
  }
}

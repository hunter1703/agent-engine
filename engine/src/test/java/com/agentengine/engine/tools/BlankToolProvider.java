package com.agentengine.engine.tools;

import com.agentengine.engine.api.ToolProvider;

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
  public Tool create(final Map<String, Object> toolConfig) {
    return null;
  }
}

package com.agentengine.engine.tools;

import com.agentengine.engine.api.Tool;
import com.agentengine.engine.api.ToolProvider;

import java.util.Map;

public class TestToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "test-agent";
  }

  @Override
  public String toolName() {
    return "fake";
  }

  @Override
  public Tool create(final Map<String, Object> toolConfig) {
    return new Tool() {
      @Override
      public String name() {
        return "fake";
      }

      @Override
      public String description() {
        return "test tool";
      }

      @Override
      public String execute(final Map<String, Object> args) {
        Object prefix = toolConfig == null ? null : toolConfig.get("prefix");
        return (prefix == null ? "" : prefix.toString()) + args.getOrDefault("value", "");
      }
    };
  }
}

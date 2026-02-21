package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import io.reactivex.rxjava3.core.Single;

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
  public BaseTool create(final AgentContext agentContext, final Map<String, Object> toolConfig) {
    return new BaseTool("fake", "test tool") {
      @Override
      public Single<Map<String, Object>> runAsync(final Map<String, Object> args, final ToolContext toolContext) {
        final Object prefix = toolConfig == null ? null : toolConfig.get("prefix");
        final String output = (prefix == null ? "" : prefix.toString()) + args.getOrDefault("value", "");
        return Single.just(Map.of("output", output));
      }
    };
  }
}

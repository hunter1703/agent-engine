package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;

public class TestToolProvider implements ToolProvider {
  private static final String TOOL_NAME = "fake";
  private static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, "test-agent", false, Map.of());

  @Override
  public List<ToolDescriptor> tools() {
    return List.of(DESCRIPTOR);
  }

  @Override
  public Tool create(
      final AgentContext agentContext,
      final String toolName,
      final Map<String, Object> toolConfig) {
    if (!TOOL_NAME.equals(toolName)) {
      return null;
    }
    return new BaseTool(TOOL_NAME, "test tool") {
      @Override
      public Single<Map<String, Object>> runAsync(
          final Map<String, Object> args, final ToolContext toolContext) {
        final Object prefix = toolConfig == null ? null : toolConfig.get("prefix");
        final String output =
            (prefix == null ? "" : prefix.toString()) + args.getOrDefault("value", "");
        return Single.just(Map.of("output", output));
      }
    };
  }
}

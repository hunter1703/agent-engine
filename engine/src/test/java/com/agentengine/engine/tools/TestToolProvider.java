package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.google.adk.tools.BaseTool;
import java.util.List;
import java.util.Map;

public class TestToolProvider implements ToolProvider {
  private static final String TOOL_NAME = "fake";
  private static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, "Fake tool for tests.", List.of("test-agent"), Map.of());

  @Override
  public List<ToolDescriptor> tools() {
    return List.of(DESCRIPTOR);
  }

  @Override
  public BaseTool create(
      final AgentContext agentContext,
      final String toolName,
      final Map<String, Object> toolConfig) {
    if (!TOOL_NAME.equals(toolName)) {
      return null;
    }
    return new StubTool(toolConfig, DESCRIPTOR);
  }

  private static final class StubTool extends com.agentengine.engine.api.tools.Tool {
    private final Map<String, Object> toolConfig;
    private final ToolDescriptor descriptor;

    private StubTool(final Map<String, Object> toolConfig, final ToolDescriptor descriptor) {
      super(descriptor);
      this.toolConfig = toolConfig;
      this.descriptor = descriptor;
    }

    @ToolSchema(name = "fake")
    public Map<String, Object> execute(
        @ToolSchema(name = "value", description = "Input value") final String value) {
      final Object prefix = toolConfig == null ? null : toolConfig.get("prefix");
      final String output =
          (prefix == null ? "" : prefix.toString()) + (value == null ? "" : value);
      return Map.of("output", output);
    }

    @Override
    public ToolDescriptor descriptor() {
      return descriptor;
    }
  }
}

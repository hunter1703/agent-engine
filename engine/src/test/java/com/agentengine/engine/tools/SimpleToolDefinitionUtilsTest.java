package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.google.adk.tools.BaseTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleToolDefinitionUtilsTest {

  @Test
  void buildToolMessageIncludesSchemaProperties() {
    final BaseTool tool = new SampleTool();

    final String message = ToolUtils.buildToolMessage(List.of(tool));

    assertThat(message).contains("run_cmd");
    assertThat(message).contains("tool args schema");
    assertThat(message).contains("\"command\"");
  }

  static class SampleTool extends com.agentengine.engine.api.tools.Tool {
    private static final ToolDescriptor DESCRIPTOR =
        new ToolDescriptor("run_cmd", "Description", List.of(ALL), Map.of());

    SampleTool() {
      super(DESCRIPTOR);
    }

    @ToolSchema(name = "run_cmd", description = "Run a command")
    public String execute(
        @ToolSchema(name = "command", description = "Shell command") final String command) {
      return command;
    }

    @Override
    public ToolDescriptor descriptor() {
      return DESCRIPTOR;
    }
  }
}

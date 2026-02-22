package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentToolDefinitionUtilsTest {

  @Test
  void buildToolMessageIncludesSchemaProperties() {
    final BaseTool tool = FunctionTool.create(new SampleTool(), "run");

    final String message = ToolUtils.buildToolMessage(List.of(tool));

    assertThat(message).contains("run_cmd");
    assertThat(message).contains("tool args schema");
    assertThat(message).contains("\"command\"");
  }

  static class SampleTool {
    @Schema(name = "run_cmd", description = "Run a command")
    public String run(
        @Schema(name = "command", description = "Shell command") final String command) {
      return command;
    }
  }
}

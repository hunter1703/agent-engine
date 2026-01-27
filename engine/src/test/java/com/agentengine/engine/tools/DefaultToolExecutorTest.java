package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.Tool;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultToolExecutorTest {

  @Test
  void executeRunsToolsAndCapturesResults() {
    DefaultToolExecutor executor = new DefaultToolExecutor(List.of(new EchoTool()));
    ToolCall call = new ToolCall("call-1", "echo", Map.of("text", "hi"));

    CapturingListener listener = new CapturingListener();
    List<ToolExecution> executions = executor.execute("session", "run", List.of(call), listener);

    assertThat(executions).hasSize(1);
    assertThat(executions.getFirst().getOutput()).isEqualTo("hi");
    assertThat(listener.results).containsEntry("call-1", "hi");
  }

  @Test
  void executeMarksUnknownTools() {
    DefaultToolExecutor executor = new DefaultToolExecutor(List.of());
    ToolCall call = new ToolCall("call-1", "unknown", Map.of());

    List<ToolExecution> executions = executor.execute("session", "run", List.of(call), new CapturingListener());

    assertThat(executions).hasSize(1);
    assertThat(executions.getFirst().getStatus()).isEqualTo("unknown");
  }

  private record EchoTool() implements Tool {
    @Override
    public String name() {
      return "echo";
    }

    @Override
    public String description() {
      return "Echoes text";
    }

    @Override
    public String execute(final Map<String, Object> args) {
      return args.get("text").toString();
    }
  }

  private static final class CapturingListener implements AgentListener {
    private final Map<String, String> results = new HashMap<>();

    @Override
    public void onToolCallResult(final String sessionId, final String toolCallId, final String content) {
      results.put(toolCallId, content);
    }
  }
}

package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.AgentTool;
import com.agentengine.engine.api.tools.annotations.ToolConstructor;
import com.agentengine.engine.api.tools.annotations.ToolParam;
import com.google.adk.tools.BaseTool;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolProviderTest {

  @Test
  void createsAnnotatedToolWithConfigAndContext() {
    Tool toolInstance = new SampleAutoTool();
    Instance<Tool> tools = mock(Instance.class);
    when(tools.iterator()).thenReturn(List.of(toolInstance).iterator());
    doNothing().when(tools).destroy(toolInstance);

    AgentToolProvider provider = new AgentToolProvider(tools);

    AgentConfig config = new AgentConfig();
    config.setId("agent-id");
    AgentContext context = new AgentContext(config, null);

    BaseTool created = provider.create(context, "sample-tool", Map.of("prefix", "pre-"));

    assertThat(provider.tools()).extracting(ToolDescriptor::name).contains("sample-tool");
    Map<String, Object> output =
        created.runAsync(Map.of("value", "fix", "arg0", "fix"), null).blockingGet();
    assertThat(output).containsEntry("output", "pre-fix").containsEntry("agentId", "agent-id");
  }

  @AgentTool
  public static final class SampleAutoTool implements Tool {
    private static final String TOOL_NAME = "sample-tool";
    private static final ToolDescriptor DESCRIPTOR =
        new ToolDescriptor(TOOL_NAME, List.of(Tool.ALL), Map.of());
    private final String prefix;
    private final String agentId;

    public SampleAutoTool() {
      this(null, null);
    }

    @ToolConstructor
    public SampleAutoTool(
        @ToolParam("prefix") final String prefix,
        @ToolParam(ToolParam.AGENT_CONTEXT) final AgentContext agentContext) {
      this.prefix = prefix == null ? "" : prefix;
      this.agentId = agentContext == null ? null : agentContext.agentId();
    }

    public Map<String, Object> execute(final String value) {
      return Map.of("output", prefix + value, "agentId", agentId);
    }

    @Override
    public ToolDescriptor descriptor() {
      return DESCRIPTOR;
    }
  }
}

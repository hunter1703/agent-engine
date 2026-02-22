package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolSuite;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.BaseTool;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ToolSuiteSupportTest {

  @Test
  void suiteSelectionExpandsTools() {
    ToolProvider provider = new SuiteToolProvider();
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));
    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(List.<ToolSuite>of().iterator());

    ToolRegistry registry = new ToolRegistry(providers, suites);
    ToolsConfig toolsConfig = new ToolsConfig("planning", Map.of());

    AgentConfig config = new AgentConfig();
    config.setId("agent-id");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    List<BaseTool> tools = registry.loadTools(context, List.of(toolsConfig));

    assertThat(tools).hasSize(1);
    assertThat(tools.get(0).name()).isEqualTo("suite_member");
  }

  private static final class SuiteToolProvider implements ToolProvider, ToolSuite {
    private static final ToolDescriptor SUITE_DESCRIPTOR =
        new ToolDescriptor("planning", List.of("ALL"), Map.of());
    private static final ToolDescriptor MEMBER_DESCRIPTOR =
        new ToolDescriptor("suite_member", List.of("ALL"), Map.of());

    @Override
    public List<ToolDescriptor> tools() {
      return List.of(SUITE_DESCRIPTOR, MEMBER_DESCRIPTOR);
    }

    @Override
    public List<String> toolNames() {
      return List.of(MEMBER_DESCRIPTOR.name());
    }

    @Override
    public BaseTool create(
        final AgentContext agentContext,
        final String toolName,
        final Map<String, Object> toolConfig) {
      if (!MEMBER_DESCRIPTOR.name().equals(toolName)) {
        return null;
      }
      return com.google.adk.tools.FunctionTool.create(new SuiteMemberTool(), "execute");
    }

    @Override
    public ToolDescriptor descriptor() {
      return SUITE_DESCRIPTOR;
    }
  }

  private static final class SuiteMemberTool implements Tool {
    @Override
    public ToolDescriptor descriptor() {
      return new ToolDescriptor("suite_member", List.of("ALL"), Map.of());
    }

    @Schema(name = "suite_member")
    public Map<String, Object> execute() {
      return Map.of("status", "ok");
    }
  }
}

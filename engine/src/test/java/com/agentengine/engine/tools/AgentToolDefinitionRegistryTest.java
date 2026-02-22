package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.ToolEntity;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import io.reactivex.rxjava3.core.Single;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentToolDefinitionRegistryTest {

  @Test
  void loadToolsFiltersByAgentId() {
    ToolProvider provider =
        new StubToolProvider(new ToolDescriptor("fake", "test-agent", false, Map.of()));
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));

    ToolRegistry registry = new ToolRegistry(providers);
    Map<String, Object> toolConfigs = Map.of("prefix", "pre-");
    ToolsConfig toolsConfig = new ToolsConfig("fake", toolConfigs);

    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    List<BaseTool> tools = registry.loadTools(context, List.of(toolsConfig));

    assertThat(tools).isNotEmpty();
    BaseTool tool = tools.stream().filter(t -> t.name().equals("fake")).findFirst().orElseThrow();
    Map<String, Object> result = tool.runAsync(Map.of("value", "fix"), null).blockingGet();
    assertThat(result).containsEntry("output", "pre-fix");
  }

  @Test
  void loadToolsSkipsMismatchedAgentAndNullConfig() {
    ToolProvider provider =
        new StubToolProvider(new ToolDescriptor("fake", "other-agent", false, Map.of()));
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));

    ToolRegistry registry = new ToolRegistry(providers);
    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    assertThat(registry.loadTools(context, List.of())).isEmpty();
    assertThat(registry.loadTools(context, null)).isEmpty();
    assertThat(registry.loadTools(context, List.of(new ToolsConfig("fake", Map.of())))).isEmpty();
  }

  @Test
  void loadToolsSkipsWhenNoEnabledTools() {
    ToolProvider provider =
        new StubToolProvider(new ToolDescriptor("fake", "test-agent", false, Map.of()));
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));

    ToolRegistry registry = new ToolRegistry(providers);
    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());

    assertThat(registry.loadTools(context, List.of())).isEmpty();
  }

  @Test
  void getAvailableToolsReturnsAllAndAgentSpecific() {
    ToolDescriptor globalTool = new ToolDescriptor("global-tool", "ALL", false, Map.of());
    ToolDescriptor agentTool = new ToolDescriptor("agent-tool", "custom-agent", false, Map.of());
    ToolDescriptor otherTool = new ToolDescriptor("other-tool", "other-agent", false, Map.of());
    ToolDescriptor subTool = new ToolDescriptor("sub-tool", "ALL", true, Map.of());
    ToolProvider provider = new StubToolProvider(globalTool, agentTool, otherTool, subTool);

    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));

    ToolRegistry registry = new ToolRegistry(providers);

    List<ToolEntity> tools = registry.getAvailableTools("custom-agent");

    assertThat(tools).hasSize(2);
    assertThat(tools)
        .extracting(ToolEntity::getName)
        .containsExactlyInAnyOrder("global-tool", "agent-tool");
  }

  private static final class StubToolProvider implements ToolProvider {
    private final List<ToolDescriptor> descriptors;

    private StubToolProvider(final ToolDescriptor... descriptors) {
      this.descriptors = List.of(descriptors);
    }

    @Override
    public List<ToolDescriptor> tools() {
      return descriptors;
    }

    @Override
    public BaseTool create(
        final AgentContext agentContext,
        final String toolName,
        final Map<String, Object> toolConfig) {
      if (descriptors.stream().noneMatch(descriptor -> descriptor.name().equals(toolName))) {
        return null;
      }
      final String prefix =
          toolConfig == null ? "" : Objects.toString(toolConfig.get("prefix"), "");
      return new BaseTool(toolName, "test tool") {
        @Override
        public Single<Map<String, Object>> runAsync(
            final Map<String, Object> args, final ToolContext toolContext) {
          final String value = Objects.toString(args.get("value"), "");
          return Single.just(Map.of("output", prefix + value));
        }
      };
    }
  }
}

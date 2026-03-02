package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolSuite;
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

class SimpleToolDefinitionRegistryTest {

  @Test
  void loadToolsFiltersByAgentId() {
    ToolProvider provider =
        new StubToolProvider(new ToolDescriptor("fake", "Fake tool.", List.of("test-agent"), Map.of()));
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));
    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(List.<ToolSuite>of().iterator());

    ToolRegistry registry = new ToolRegistry(providers, suites);
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
        new StubToolProvider(new ToolDescriptor("fake", "Fake tool.", List.of("other-agent"), Map.of()));
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));
    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(List.<ToolSuite>of().iterator());

    ToolRegistry registry = new ToolRegistry(providers, suites);
    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    // Core built-in tools are added by builders.
    assertThat(registry.loadTools(context, List.of())).isEmpty();
    assertThat(registry.loadTools(context, null)).isEmpty();
    // "fake" belongs to "other-agent", so it is skipped for "test-agent"
    assertThat(registry.loadTools(context, List.of(new ToolsConfig("fake", Map.of())))).isEmpty();
  }

  @Test
  void loadToolsSkipsWhenNoEnabledTools() {
    ToolProvider provider =
        new StubToolProvider(new ToolDescriptor("fake", "Fake tool.", List.of("test-agent"), Map.of()));
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));
    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(List.<ToolSuite>of().iterator());

    ToolRegistry registry = new ToolRegistry(providers, suites);
    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());

    // Registry returns no tools.
    assertThat(registry.loadTools(context, List.of())).isEmpty();
  }

  @Test
  void getAvailableToolsReturnsAllAndAgentSpecific() {
    ToolDescriptor globalTool = new ToolDescriptor("global-tool", "Global tool.", List.of("ALL"), Map.of());
    ToolDescriptor agentTool = new ToolDescriptor("agent-tool", "Agent tool.", List.of("custom-agent"), Map.of());
    ToolDescriptor otherTool = new ToolDescriptor("other-tool", "Other tool.", List.of("other-agent"), Map.of());
    ToolDescriptor subTool = new ToolDescriptor("sub-tool", "Sub tool.", List.of("ALL"), Map.of());
    ToolProvider provider = new StubToolProvider(globalTool, agentTool, otherTool, subTool);

    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));
    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(List.<ToolSuite>of().iterator());

    ToolRegistry registry = new ToolRegistry(providers, suites);

    List<ToolDescriptor> tools = registry.getVisibleTools("custom-agent");

    assertThat(tools).hasSize(3);
    assertThat(tools)
        .extracting(ToolDescriptor::name)
        .containsExactlyInAnyOrder("global-tool", "agent-tool", "sub-tool");
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

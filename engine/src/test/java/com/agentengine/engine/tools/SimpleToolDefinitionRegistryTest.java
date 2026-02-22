package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.tools.BaseTool;
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
        new StubToolProvider(new ToolDescriptor("fake", List.of("test-agent"), Map.of()));
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
        new StubToolProvider(new ToolDescriptor("fake", List.of("other-agent"), Map.of()));
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
        new StubToolProvider(new ToolDescriptor("fake", List.of("test-agent"), Map.of()));
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
    ToolDescriptor globalTool = new ToolDescriptor("global-tool", List.of("ALL"), Map.of());
    ToolDescriptor agentTool = new ToolDescriptor("agent-tool", List.of("custom-agent"), Map.of());
    ToolDescriptor otherTool = new ToolDescriptor("other-tool", List.of("other-agent"), Map.of());
    ToolDescriptor subTool = new ToolDescriptor("sub-tool", List.of("ALL"), Map.of());
    ToolProvider provider = new StubToolProvider(globalTool, agentTool, otherTool, subTool);

    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));

    ToolRegistry registry = new ToolRegistry(providers);

    List<ToolDescriptor> tools = registry.getAvailableTools("custom-agent");

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
    public com.agentengine.engine.api.tools.Tool create(
        final AgentContext agentContext,
        final String toolName,
        final Map<String, Object> toolConfig) {
      if (descriptors.stream().noneMatch(descriptor -> descriptor.name().equals(toolName))) {
        return null;
      }
      final String prefix =
          toolConfig == null ? "" : Objects.toString(toolConfig.get("prefix"), "");
      final ToolDescriptor descriptor =
          descriptors.stream()
              .filter(item -> item.name().equals(toolName))
              .findFirst()
              .orElseThrow();
      return new StubTool(toolName, prefix, descriptor);
    }
  }

  private static final class StubTool implements com.agentengine.engine.api.tools.Tool {
    private final String name;
    private final String prefix;
    private final ToolDescriptor descriptor;

    private StubTool(final String name, final String prefix, final ToolDescriptor descriptor) {
      this.name = name;
      this.prefix = prefix;
      this.descriptor = descriptor;
    }

    public Map<String, Object> execute(final String value) {
      return Map.of("output", prefix + value);
    }

    @Override
    public ToolDescriptor descriptor() {
      return descriptor;
    }
  }
}

package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolSuite;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.adk.sessions.InMemorySessionService;
import io.reactivex.rxjava3.core.Single;
import jakarta.enterprise.inject.Instance;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  @Test
  @SuppressWarnings("unchecked")
  void loadToolsFiltersByAgentId() {
    ToolProvider provider = mock(ToolProvider.class);
    when(provider.agentId()).thenReturn("test-agent");
    when(provider.name()).thenReturn("fake");
    BaseTool fakeTool = new BaseTool("fake", "test tool") {
      @Override
      public Single<Map<String, Object>> runAsync(final Map<String, Object> args, final ToolContext toolContext) {
        return Single.just(Map.of("output", "pre-" + args.get("value")));
      }
    };
    when(provider.create(any(AgentContext.class), anyMap())).thenReturn(fakeTool);

    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));

    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(Collections.emptyIterator());
    when(suites.stream()).thenReturn(Stream.empty());

    ToolRegistry registry = new ToolRegistry(providers, suites);
    Map<String, Object> toolConfigs = Map.of("prefix", "pre-");
    ToolsConfig toolsConfig = new ToolsConfig("fake", toolConfigs);

    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    List<BaseTool> tools = registry.loadTools(context, List.of(toolsConfig));

    assertThat(tools).isNotEmpty();
    BaseTool tool = tools.stream().filter(t -> t.name().equals("fake")).findFirst().orElseThrow();
    Map<String, Object> result = (Map<String, Object>) tool.runAsync(Map.of("value", "fix"), null).blockingGet();
    assertThat(result).containsEntry("output", "pre-fix");
  }

  @Test
  void loadToolsExpandsToolSuite() {
    ToolProvider tool1 = buildProvider("tool1");
    ToolProvider tool2 = buildProvider("tool2");

    ToolSuite suite = mock(ToolSuite.class);
    when(suite.agentId()).thenReturn("ALL");
    when(suite.name()).thenReturn("test-suite");
    when(suite.toolProviders()).thenReturn(List.of(tool1, tool2));

    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(Collections.emptyIterator());
    when(providers.stream()).thenReturn(Stream.empty());

    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(List.of(suite).iterator());
    when(suites.stream()).thenReturn(Stream.of(suite));

    ToolRegistry registry = new ToolRegistry(providers, suites);
    ToolsConfig toolsConfig = new ToolsConfig("test-suite", Map.of());

    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    List<BaseTool> tools = registry.loadTools(context, List.of(toolsConfig));

    assertThat(tools).hasSize(2);
    assertThat(tools.get(0).name()).isEqualTo("tool1");
    assertThat(tools.get(1).name()).isEqualTo("tool2");
  }

  @Test
  @SuppressWarnings("unchecked")
  void loadToolsSkipsMismatchedAgentAndNullConfig() {
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.<ToolProvider>of().iterator());
    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(Collections.emptyIterator());

    ToolRegistry registry = new ToolRegistry(providers, suites);
    AgentConfig config = new AgentConfig();
    config.setId("other-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    assertThat(registry.loadTools(context, List.of())).isEmpty();
    assertThat(registry.loadTools(context, null)).isEmpty();
  }

  @Test
  void loadToolsSkipsWhenNoEnabledTools() {
    ToolProvider provider = buildProvider("fake");
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));

    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(Collections.emptyIterator());
    when(suites.stream()).thenReturn(Stream.empty());

    ToolRegistry registry = new ToolRegistry(providers, suites);
    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());

    assertThat(registry.loadTools(context, List.of())).isEmpty();
  }

  private static Map<String, Object> anyMap() {
    return any();
  }

  private static ToolProvider buildProvider(final String toolName) {
    return buildProvider(toolName, "ALL", false);
  }

  private static ToolProvider buildProvider(final String toolName, final String agentId, boolean subTool) {
    ToolProvider provider = mock(ToolProvider.class);
    when(provider.agentId()).thenReturn(agentId);
    when(provider.name()).thenReturn(toolName);
    when(provider.isSubTool()).thenReturn(subTool);
    BaseTool tool = new BaseTool(toolName, "test tool") {
      @Override
      public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
        return Single.just(Map.of("status", "ok"));
      }
    };
    when(provider.create(any(AgentContext.class), anyMap())).thenReturn(tool);
    return provider;
  }

  @Test
  void getAvailableToolsReturnsAllAndAgentSpecific() {
    ToolProvider globalProvider = buildProvider("global-tool", "ALL", false);
    ToolProvider agentSpecificProvider = buildProvider("agent-tool", "custom-agent", false);
    ToolProvider otherAgentProvider = buildProvider("other-tool", "other-agent", false);
    ToolProvider subToolProvider = buildProvider("sub-tool", "ALL", true);

    ToolSuite globalSuite = mock(ToolSuite.class);
    when(globalSuite.agentId()).thenReturn("ALL");
    when(globalSuite.name()).thenReturn("global-suite");

    ToolSuite agentSuite = mock(ToolSuite.class);
    when(agentSuite.agentId()).thenReturn("custom-agent");
    when(agentSuite.name()).thenReturn("agent-suite");

    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator())
        .thenReturn(List.of(globalProvider, agentSpecificProvider, otherAgentProvider, subToolProvider).iterator());
    when(providers.stream())
        .thenReturn(Stream.of(globalProvider, agentSpecificProvider, otherAgentProvider, subToolProvider));

    Instance<ToolSuite> suites = mock(Instance.class);
    when(suites.iterator()).thenReturn(List.of(globalSuite, agentSuite).iterator());
    when(suites.stream()).thenReturn(Stream.of(globalSuite, agentSuite));

    ToolRegistry registry = new ToolRegistry(providers, suites);

    List<com.agentengine.engine.api.beans.ToolEntity> tools = registry.getAvailableTools("custom-agent");

    assertThat(tools).hasSize(4);
    assertThat(tools).extracting(com.agentengine.engine.api.beans.ToolEntity::getName)
        .containsExactlyInAnyOrder("global-tool", "agent-tool", "global-suite", "agent-suite");
  }
}

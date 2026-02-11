package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.ToolProvider;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.adk.sessions.InMemorySessionService;
import io.reactivex.rxjava3.core.Single;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  @Test
  @SuppressWarnings("unchecked")
  void loadToolsFiltersByAgentIdAndEnabledList() {
    ToolProvider provider = mock(ToolProvider.class);
    when(provider.agentId()).thenReturn("test-agent");
    when(provider.toolName()).thenReturn("fake");
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

    ToolRegistry registry = new ToolRegistry(providers);
    Map<String, Map<String, Object>> toolConfigs = Map.of("fake", Map.of("prefix", "pre-"));
    ToolsConfig toolsConfig = new ToolsConfig();
    toolsConfig.setEnabled(List.of("ALL"));
    toolsConfig.setConfigs(toolConfigs);

    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    List<BaseTool> tools = registry.loadTools(context, toolsConfig);

    assertThat(tools).isNotEmpty();
    BaseTool tool = tools.stream().filter(t -> t.name().equals("fake")).findFirst().orElseThrow();
    Map<String, Object> result = (Map<String, Object>) tool.runAsync(Map.of("value", "fix"), null).blockingGet();
    assertThat(result).containsEntry("output", "pre-fix");

    toolsConfig.setEnabled(List.of("other"));
    List<BaseTool> filtered = registry.loadTools(context, toolsConfig);

    assertThat(filtered).isEmpty();
  }

  @Test
  void loadToolsExpandsPlanningToolGroup() {
    ToolProvider createPlanProvider = buildProvider("create_plan");
    ToolProvider finishPlanProvider = buildProvider("finish_plan");
    ToolProvider otherProvider = buildProvider("other_tool");

    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(createPlanProvider, finishPlanProvider, otherProvider).iterator());
    when(providers.stream()).thenReturn(Stream.of(createPlanProvider, finishPlanProvider, otherProvider));

    ToolRegistry registry = new ToolRegistry(providers);
    ToolsConfig toolsConfig = new ToolsConfig();
    toolsConfig.setEnabled(List.of("planning"));

    AgentConfig config = new AgentConfig();
    config.setId("test-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    List<BaseTool> tools = registry.loadTools(context, toolsConfig);

    assertThat(tools).hasSize(2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void loadToolsSkipsMismatchedAgentAndNullConfig() {
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.<ToolProvider>of().iterator());

    ToolRegistry registry = new ToolRegistry(providers);
    AgentConfig config = new AgentConfig();
    config.setId("other-agent");
    AgentContext context = new AgentContext(config, new InMemorySessionService());
    assertThat(registry.loadTools(context, new ToolsConfig())).isEmpty();
    assertThat(registry.loadTools(context, null)).isEmpty();
  }

  private static Map<String, Object> anyMap() {
    return any();
  }

  private static ToolProvider buildProvider(final String toolName) {
    ToolProvider provider = mock(ToolProvider.class);
    when(provider.agentId()).thenReturn("ALL");
    when(provider.toolName()).thenReturn(toolName);
    BaseTool tool = FunctionTool.create(new Object() {
      public Map<String, Object> run(Map<String, Object> args) {
        return Map.of("status", "ok");
      }
    }, "run");
    when(provider.create(any(AgentContext.class), anyMap())).thenReturn(tool);
    return provider;
  }
}

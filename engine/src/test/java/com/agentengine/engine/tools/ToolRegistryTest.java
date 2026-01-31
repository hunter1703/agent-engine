package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.ToolProvider;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
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
    BaseTool fakeTool = FunctionTool.create(new Object() {
      public Map<String, Object> run(Map<String, Object> args) {
        return Map.of("output", "pre-" + args.get("value"));
      }
    }, "run");
    when(provider.create(anyMap())).thenReturn(fakeTool);

    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.of(provider).iterator());
    when(providers.stream()).thenReturn(Stream.of(provider));

    ToolRegistry registry = new ToolRegistry(providers);
    Map<String, Map<String, Object>> toolConfigs = Map.of("fake", Map.of("prefix", "pre-"));
    ToolsConfig toolsConfig = new ToolsConfig();
    toolsConfig.setEnabled(List.of("ALL"));
    toolsConfig.setConfigs(toolConfigs);

    List<BaseTool> tools = registry.loadTools("test-agent", toolsConfig);

    assertThat(tools).hasSize(2); // mocked tool + global PlanTool
    BaseTool tool = tools.stream().filter(t -> t.name().equals("fake")).findFirst().orElseThrow();
    Map<String, Object> result = (Map<String, Object>) tool.runAsync(Map.of("value", "fix"), null)
        .blockingGet();
    assertThat(result).containsEntry("output", "pre-fix");

    toolsConfig.setEnabled(List.of("other"));
    List<BaseTool> filtered = registry.loadTools("test-agent", toolsConfig);

    assertThat(filtered).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void loadToolsSkipsMismatchedAgentAndNullConfig() {
    Instance<ToolProvider> providers = mock(Instance.class);
    when(providers.iterator()).thenReturn(List.<ToolProvider>of().iterator());

    ToolRegistry registry = new ToolRegistry(providers);
    assertThat(registry.loadTools("other-agent", new ToolsConfig())).isEmpty();
    assertThat(registry.loadTools("test-agent", null)).isEmpty();
  }

  private static Map<String, Object> anyMap() {
    return org.mockito.ArgumentMatchers.anyMap();
  }
}

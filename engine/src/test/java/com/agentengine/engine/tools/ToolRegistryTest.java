package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.google.adk.tools.BaseTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  @Test
  void loadToolsFiltersByAgentIdAndEnabledList() {
    ToolRegistry registry = new ToolRegistry();
    Map<String, Map<String, Object>> toolConfigs = Map.of("fake", Map.of("prefix", "pre-"));
    ToolsConfig toolsConfig = new ToolsConfig();
    toolsConfig.setEnabled(List.of("ALL"));
    toolsConfig.setConfigs(toolConfigs);

    List<BaseTool> tools = registry.loadTools("test-agent", toolsConfig);

    assertThat(tools).hasSize(1);
    Map<String, Object> result = tools.getFirst().runAsync(Map.of("value", "fix"), null).blockingGet();
    assertThat(result).containsEntry("output", "pre-fix");

    toolsConfig.setEnabled(List.of("other"));
    List<BaseTool> filtered = registry.loadTools("test-agent", toolsConfig);

    assertThat(filtered).isEmpty();
  }

  @Test
  void loadToolsSkipsMismatchedAgentAndNullConfig() {
    ToolRegistry registry = new ToolRegistry();
    assertThat(registry.loadTools("other-agent", new ToolsConfig())).isEmpty();
    assertThat(registry.loadTools("test-agent", null)).isEmpty();
  }
}

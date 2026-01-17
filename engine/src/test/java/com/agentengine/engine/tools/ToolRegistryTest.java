package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.config.ToolsConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  @Test
  void loadToolsFiltersByAgentNameAndEnabledList() {
    Map<String, Map<String, Object>> toolConfigs = Map.of("fake", Map.of("prefix", "pre-"));
    ToolsConfig toolsConfig = new ToolsConfig();
    toolsConfig.setEnabled(List.of("ALL"));
    toolsConfig.setConfigs(toolConfigs);

    List<AgentTool> tools = ToolRegistry.loadTools("test-agent", toolsConfig);

    assertThat(tools).hasSize(1);
    assertThat(tools.getFirst().execute(Map.of("value", "fix"))).isEqualTo("pre-fix");

    toolsConfig.setEnabled(List.of("other"));
    List<AgentTool> filtered = ToolRegistry.loadTools("test-agent", toolsConfig);

    assertThat(filtered).isEmpty();
  }
}

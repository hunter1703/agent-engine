package com.localagent.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.localagent.engine.beans.config.AgentConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  @Test
  void loadToolsFiltersByAgentNameAndEnabledList() {
    AgentConfig config = AgentConfig.empty();
    Map<String, Map<String, Object>> toolConfigs = Map.of("fake", Map.of("prefix", "pre-"));

    List<AgentTool> tools =
        ToolRegistry.loadTools("test-agent", List.of("ALL"), toolConfigs, config);

    assertThat(tools).hasSize(1);
    assertThat(tools.getFirst().execute(Map.of("value", "fix"))).isEqualTo("pre-fix");

    List<AgentTool> filtered =
        ToolRegistry.loadTools("test-agent", List.of("other"), toolConfigs, config);

    assertThat(filtered).isEmpty();
  }
}

package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolsConfigTest {

  @Test
  void getEnabledReturnsCopy() {
    ToolsConfig config = new ToolsConfig();
    config.setEnabled(List.of("tool1", "tool2"));

    List<String> enabled = config.getEnabled();
    assertThat(enabled).containsExactly("tool1", "tool2");

    enabled.clear();
    assertThat(config.getEnabled()).containsExactly("tool1", "tool2");
  }

  @Test
  void setConfigsCopiesNullToEmptyMap() {
    ToolsConfig config = new ToolsConfig();
    config.setConfigs(null);

    assertThat(config.getConfigs()).isEmpty();

    config.setConfigs(Map.of("tool", Map.of("a", 1)));
    assertThat(config.getConfigs()).containsKey("tool");
  }
}

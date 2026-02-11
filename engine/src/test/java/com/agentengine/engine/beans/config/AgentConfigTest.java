package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

  @Test
  void testDefaultConfig() {
    AgentConfig config = new AgentConfig();
    assertThat(config.getType()).isEqualTo("default");
  }

  @Test
  void validateRequiresNameAndModels() {
    AgentConfig config = new AgentConfig();
    config.setId("agent");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setModel(model("reasoner"));

    config.validate();
  }

  private static AgentModelConfig model(final String id) {
    AgentModelConfig config = new AgentModelConfig();
    config.setModelId(id);
    config.setSystemPrompt("system");
    return config;
  }
}

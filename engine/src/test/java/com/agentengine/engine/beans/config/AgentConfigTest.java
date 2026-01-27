package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

  @Test
  void emptyDefaultsToHybridConfig() {
    AgentConfig config = AgentConfig.empty();

    assertThat(config).isInstanceOf(HybridAgentConfig.class);
    assertThat(config.getType()).isEqualTo("hybrid");
  }

  @Test
  void validateRequiresNameAndModels() {
    HybridAgentConfig config = new HybridAgentConfig();
    config.setAgentId("agent");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setModel(model("reasoner"));
    config.setRouterModel(model("router"));
    config.setPlanningModel(model("planner"));

    config.validate();
  }

  private static AgentModelConfig model(final String id) {
    AgentModelConfig config = new AgentModelConfig();
    config.setModelId(id);
    return config;
  }
}

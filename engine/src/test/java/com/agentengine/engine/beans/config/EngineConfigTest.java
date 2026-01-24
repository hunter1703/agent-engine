package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import org.junit.jupiter.api.Test;

class EngineConfigTest {

  @Test
  void hybridConfigRequiresRouterAndPlanningModels() {
    HybridAgentConfig config = new HybridAgentConfig();
    config.setName("agent");
    config.setModel(model("reasoner"));

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setRouterModel(model("router"));
    config.setPlanningModel(model("planner"));

    config.validate();
  }

  private static AgentModelConfig model(final String id) {
    AgentModelConfig model = new AgentModelConfig();
    model.setModelId(id);
    return model;
  }
}

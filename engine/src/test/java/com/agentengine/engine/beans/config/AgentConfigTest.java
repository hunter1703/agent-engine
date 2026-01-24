package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

  @Test
  void defaultsProvideNonNullSections() {
    AgentConfig config = AgentConfig.empty();

    assertThat(config.getTools()).isNotNull();
    assertThat(config.getEngine()).isNotNull();
    assertThat(config.getContext()).isNotNull();
    assertThat(config.getStateStore()).isNotNull();
  }

  @Test
  void validateDelegatesToEngineConfig() {
    AgentConfig config = AgentConfig.empty();

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    HybridAgentConfig engine = (HybridAgentConfig) config.getEngine();
    engine.setReasoningModelId("reasoner.json");
    engine.setSystemPrompt("prompt");
    engine.setRouterModelId("tool.json");

    config.validate();
  }
}

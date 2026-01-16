package com.localagent.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    EngineConfig engine = config.getEngine();
    engine.setReasoning("reasoner.json");
    engine.setPrompt("prompt");

    config.validate();
  }
}

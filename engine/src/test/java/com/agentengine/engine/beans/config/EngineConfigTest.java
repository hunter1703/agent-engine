package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EngineConfigTest {

  @Test
  void validateRequiresReasoningAndPrompt() {
    EngineConfig config = new EngineConfig();
    config.setPrompt("prompt");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setReasoning("model.json");
    config.setPrompt(null);

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validateEnsuresRouterDependencies() {
    EngineConfig config = new EngineConfig();
    config.setReasoning("reasoner");
    config.setPrompt("prompt");
    config.setRouter("router");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setTool("tool");
    config.setHeavy("heavy");
    config.setRouter(null);

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validateAllowsValidConfigurations() {
    EngineConfig config = new EngineConfig();
    config.setReasoning("reasoner");
    config.setPrompt("prompt");
    config.setTool("tool");
    config.setRouter("router");

    config.validate();
  }

  @Test
  void toolRetryLimitDefaultsAndCanBeSet() {
    EngineConfig config = new EngineConfig();
    assertThat(config.getToolRetryLimit()).isEqualTo(2);

    config.setToolRetryLimit(5);
    assertThat(config.getToolRetryLimit()).isEqualTo(5);
  }
}

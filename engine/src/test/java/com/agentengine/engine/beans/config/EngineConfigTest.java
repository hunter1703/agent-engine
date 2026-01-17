package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EngineConfigTest {

  @Test
  void hybridRequiresReasoningPromptAndTool() {
    HybridEngineConfig config = new HybridEngineConfig();
    config.setSystemPrompt("prompt");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setReasoning("model.json");
    config.setSystemPrompt(null);

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setSystemPrompt("prompt");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setTool("tool");

    config.validate();
  }

  @Test
  void routerRequiresRouterAndTargets() {
    RouterEngineConfig config = new RouterEngineConfig();
    config.setReasoning("reasoner");
    config.setSystemPrompt("prompt");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setRouter("router");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setTool("tool");

    config.validate();
  }

  @Test
  void toolRetryLimitDefaultsAndCanBeSet() {
    HybridEngineConfig config = new HybridEngineConfig();
    assertThat(config.getToolRetryLimit()).isEqualTo(2);

    config.setToolRetryLimit(5);
    assertThat(config.getToolRetryLimit()).isEqualTo(5);
  }
}

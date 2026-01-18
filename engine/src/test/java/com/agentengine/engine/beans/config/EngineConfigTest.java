package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EngineConfigTest {

  @Test
  void hybridConfigStoresInvocationLimit() {
    final HybridEngineConfig config = new HybridEngineConfig();
    config.setInvocationLimit(3);
    config.setSystemPrompt("system");
    config.setReasoning("reasoner");
    config.setTool("tool");

    config.validate();

    assertThat(config.getInvocationLimit()).isEqualTo(3);
  }

  @Test
  void routerConfigStoresInvocationLimit() {
    final RouterEngineConfig config = new RouterEngineConfig();
    config.setInvocationLimit(4);
    config.setSystemPrompt("system");
    config.setReasoning("reasoner");
    config.setRouter("router");
    config.setTool("tool");

    config.validate();

    assertThat(config.getInvocationLimit()).isEqualTo(4);
  }
}

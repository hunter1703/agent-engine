package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import org.junit.jupiter.api.Test;

class EngineConfigTest {

  @Test
  void hybridConfigStoresInvocationLimit() {
    final HybridAgentConfig config = new HybridAgentConfig();
    config.setInvocationLimit(3);
    config.setSystemPrompt("system");
    config.setReasoningModelId("reasoner");
    config.setRouterModelId("tool");

    config.validate();

    assertThat(config.getInvocationLimit()).isEqualTo(3);
  }

  @Test
  void routerConfigStoresInvocationLimit() {
    final RouterEngineConfig config = new RouterEngineConfig();
    config.setInvocationLimit(4);
    config.setSystemPrompt("system");
    config.setReasoningModelId("reasoner");
    config.setRouter("router");
    config.setTool("tool");

    config.validate();

    assertThat(config.getInvocationLimit()).isEqualTo(4);
  }
}

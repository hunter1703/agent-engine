package com.agentengine.engine.api.beans.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class AgentModelConfigTest {

  @Test
  void defaultsToSummarizeContextManager() {
    final AgentModelConfig config = new AgentModelConfig();

    assertInstanceOf(SummarizeContextManagerConfig.class, config.getContextManagerConfig());
  }
}

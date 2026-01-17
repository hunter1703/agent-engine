package com.agentengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgentServiceCdiTest {

  @Inject AgentService agentService;

  @Test
  void injectsAgentServiceWithEngineBeans() {
    assertThat(agentService).isNotNull();
  }
}

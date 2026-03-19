package com.agentengine.engine.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentExecutionServiceImplTest {

  @Test
  void shouldNormalizeHyphenatedAgentIdIntoAdkCompatibleRuntimeAppName() {
    final String runtimeAppName = AgentExecutionServiceImpl.runtimeAppName("mgr-story-plus-echo-001");

    assertThat(runtimeAppName).isEqualTo("mgr_story_plus_echo_001");
    assertThat(runtimeAppName).matches("[A-Za-z0-9_]+");
  }

  @Test
  void shouldPreserveAlreadyCompatibleRuntimeAppName() {
    final String runtimeAppName = AgentExecutionServiceImpl.runtimeAppName("story_orchestrator_sequential");

    assertThat(runtimeAppName).isEqualTo("story_orchestrator_sequential");
  }
}

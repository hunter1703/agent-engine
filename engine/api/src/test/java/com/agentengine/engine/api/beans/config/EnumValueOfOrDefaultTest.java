package com.agentengine.engine.api.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnumValueOfOrDefaultTest {

  @Test
  void shouldReturnUnknownAgentTypeWhenValueInvalid() {
    assertThat(BaseAgentConfig.AgentType.valueOfOrDefault("invalid"))
        .isEqualTo(BaseAgentConfig.AgentType.UNKNOWN);
  }

  @Test
  void shouldParseAgentTypeWhenValueHasDifferentCase() {
    assertThat(BaseAgentConfig.AgentType.valueOfOrDefault("orchestrator"))
        .isEqualTo(BaseAgentConfig.AgentType.ORCHESTRATOR);
  }

  @Test
  void shouldReturnUnknownGuardrailActionWhenValueMissing() {
    assertThat(GuardrailAction.valueOfOrDefault(" ")).isEqualTo(GuardrailAction.UNKNOWN);
  }

  @Test
  void shouldParseGuardrailActionWhenValueValid() {
    assertThat(GuardrailAction.valueOfOrDefault("warn")).isEqualTo(GuardrailAction.WARN);
  }

  @Test
  void shouldReturnUnknownOrchestrationModeWhenValueInvalid() {
    assertThat(OrchestrationMode.valueOfOrDefault("not_a_mode"))
        .isEqualTo(OrchestrationMode.UNKNOWN);
  }

  @Test
  void shouldParseManagerOrchestrationModeWhenValueValid() {
    assertThat(OrchestrationMode.valueOfOrDefault("manager"))
        .isEqualTo(OrchestrationMode.MANAGER);
  }

  @Test
  void shouldParseParallelStoppingPolicyWhenValueValid() {
    assertThat(ParallelStoppingPolicy.valueOfOrDefault("quorum"))
        .isEqualTo(ParallelStoppingPolicy.QUORUM);
  }
}

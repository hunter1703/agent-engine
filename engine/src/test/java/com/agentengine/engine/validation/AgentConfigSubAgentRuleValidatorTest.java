package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConfigSubAgentRuleValidatorTest {

  private final AgentConfigSubAgentRuleValidator validator = new AgentConfigSubAgentRuleValidator();

  @Test
  void shouldAddErrorWhenDefaultAgentContainsSubAgents() {
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("agent-default");
    config.setSubAgentIds(List.of("sub-1"));
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors().getFirst()).contains("supported only for type=orchestrator");
  }

  @Test
  void shouldAddErrorWhenSequentialOrchestratorHasNoSubAgents() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.SEQUENTIAL);
    config.setSubAgentIds(List.of());
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors().getFirst()).contains("requires non-empty subAgentIds");
  }

  @Test
  void shouldNotAddErrorWhenTransferOrchestratorHasNoSubAgents() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.TRANSFER);
    config.setSubAgentIds(List.of());
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isFalse();
  }
}

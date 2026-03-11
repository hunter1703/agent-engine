package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.util.common.validation.ValidationCollector;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConfigSubAgentRuleValidatorTest {

  private final AgentValidator validator = new AgentValidator();

  @Test
  void shouldAddErrorWhenDefaultAgentContainsSubAgents() {
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("agent-default");
    config.setSubAgentIds(List.of("sub-1"));
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors())
        .anyMatch(error -> error.contains("supported only for type=orchestrator"));
  }

  @Test
  void shouldAddErrorWhenSequentialOrchestratorHasNoSubAgents() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.SEQUENTIAL.name());
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
    config.setOrchestrationMode(OrchestrationMode.TRANSFER.name());
    config.setSubAgentIds(List.of());
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isFalse();
  }

  @Test
  void shouldNotAddErrorWhenManagerOrchestratorHasNoSubAgents() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.MANAGER.name());
    config.setSubAgentIds(List.of());
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isFalse();
  }

  @Test
  void shouldAddErrorWhenOrchestratorReferencesMissingSubAgents() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setSubAgentIds(List.of("sub-1", "sub-2"));
    final ValidationCollector collector = new ValidationCollector();

    AgentValidator.validateOrchestratorSubAgentsExist(
        config, subAgentId -> !"sub-2".equals(subAgentId), collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors().getFirst()).contains("Sub-agent(s) not found: sub-2");
  }
}

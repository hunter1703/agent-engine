package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import org.junit.jupiter.api.Test;

class AgentConfigModelRuleValidatorTest {

  private final AgentConfigModelRuleValidator validator = new AgentConfigModelRuleValidator();

  @Test
  void shouldAddErrorWhenDefaultAgentMissingModelId() {
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("agent-default");
    config.setModelId(" ");
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors().getFirst()).contains("modelId is required");
  }

  @Test
  void shouldAddErrorWhenTransferOrchestratorMissingModelId() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.TRANSFER);
    config.setModelId(null);
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors().getFirst()).contains("orchestrationMode=transfer");
  }

  @Test
  void shouldNotAddErrorWhenParallelOrchestratorMissingModelId() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.PARALLEL);
    config.setModelId(null);
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isFalse();
  }
}

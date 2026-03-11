package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.util.common.validation.ValidationCollector;
import org.junit.jupiter.api.Test;

class AgentConfigModelRuleValidatorTest {

  private final AgentValidator validator = new AgentValidator();

  @Test
  void shouldAddErrorWhenAgentTypeMissing() {
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("agent-default");
    config.setType(" ");
    config.setModelId("model-1");
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors().getFirst()).contains("Agent type is required");
  }

  @Test
  void shouldAddErrorWhenDefaultAgentMissingModelId() {
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("agent-default");
    config.setModelId(" ");
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors().getFirst()).contains("Agent type and modelId are required");
  }

  @Test
  void shouldNotAddErrorWhenOrchestratorMissingModelId() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setModelId(null);
    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isFalse();
  }
}

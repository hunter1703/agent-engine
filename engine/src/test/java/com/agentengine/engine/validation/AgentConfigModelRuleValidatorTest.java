package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.utils.AgentValidator;
import com.agentengine.util.common.validation.ValidationCollector;
import jakarta.enterprise.inject.Instance;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentConfigModelRuleValidatorTest {

  private final AgentValidator validator = validatorWithAllSubAgentsPresent();

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

  @SuppressWarnings("unchecked")
  private static AgentValidator validatorWithAllSubAgentsPresent() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent(anyString())).thenReturn(Optional.of(mock(BaseAgentConfig.class)));
    final Instance<AgentService> agentServices = mock(Instance.class);
    when(agentServices.get()).thenReturn(agentService);
    return new AgentValidator(agentServices);
  }
}

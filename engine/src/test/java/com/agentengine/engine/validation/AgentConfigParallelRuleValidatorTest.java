package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.beans.config.ParallelStoppingPolicy;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.utils.AgentValidator;
import com.agentengine.util.common.validation.ValidationCollector;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentConfigParallelRuleValidatorTest {

  private final AgentValidator validator = validatorWithAllSubAgentsPresent();

  @Test
  void shouldAddErrorWhenQuorumExceedsSubAgentCount() {
    final OrchestratorParallelConfig parallel = new OrchestratorParallelConfig();
    parallel.setStoppingPolicy(ParallelStoppingPolicy.QUORUM.name());
    parallel.setQuorum(3);

    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.PARALLEL.name());
    config.setParallel(parallel);
    config.setSubAgentIds(List.of("sub-1", "sub-2"));

    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.errors().getFirst()).contains("quorum <= sub-agent count");
  }

  @Test
  void shouldNotAddErrorWhenQuorumWithinSubAgentCount() {
    final OrchestratorParallelConfig parallel = new OrchestratorParallelConfig();
    parallel.setStoppingPolicy(ParallelStoppingPolicy.QUORUM.name());
    parallel.setQuorum(2);

    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.PARALLEL.name());
    config.setParallel(parallel);
    config.setSubAgentIds(List.of("sub-1", "sub-2", "sub-3"));

    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isFalse();
  }

  @Test
  void shouldIgnoreValidationWhenAgentNotParallelOrchestrator() {
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.TRANSFER.name());
    config.setSubAgentIds(List.of("sub-1"));

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

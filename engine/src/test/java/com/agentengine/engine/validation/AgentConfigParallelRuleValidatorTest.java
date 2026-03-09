package com.agentengine.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.beans.config.ParallelStoppingPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConfigParallelRuleValidatorTest {

  private final AgentConfigParallelRuleValidator validator =
      new AgentConfigParallelRuleValidator();

  @Test
  void shouldAddErrorWhenQuorumExceedsSubAgentCount() {
    final OrchestratorParallelConfig parallel = new OrchestratorParallelConfig();
    parallel.setStoppingPolicy(ParallelStoppingPolicy.QUORUM);
    parallel.setQuorum(3);

    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.PARALLEL);
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
    parallel.setStoppingPolicy(ParallelStoppingPolicy.QUORUM);
    parallel.setQuorum(2);

    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("agent-orchestrator");
    config.setOrchestrationMode(OrchestrationMode.PARALLEL);
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
    config.setOrchestrationMode(OrchestrationMode.TRANSFER);
    config.setSubAgentIds(List.of("sub-1"));

    final ValidationCollector collector = new ValidationCollector();

    validator.validate(config, collector);

    assertThat(collector.hasErrors()).isFalse();
  }
}

package com.agentengine.engine.builders.agent;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.DefaultAgent;
import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import jakarta.enterprise.inject.Instance;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MultiAgentTypeExposureTest {

  @Test
  @SuppressWarnings("unchecked")
  void resolvesOrchestratorAndWorkerAgentTypes() {
    final DefaultAgentBuilder defaultBuilder = mock(DefaultAgentBuilder.class);
    when(defaultBuilder.type()).thenReturn("default");

    final OrchestratorAgentBuilder orchestratorBuilder = mock(OrchestratorAgentBuilder.class);
    when(orchestratorBuilder.type()).thenReturn("orchestrator");
    final DefaultAgent orchestratorAgent = mock(DefaultAgent.class);

    final WorkerAgentBuilder workerBuilder = mock(WorkerAgentBuilder.class);
    when(workerBuilder.type()).thenReturn("worker");
    final DefaultAgent workerAgent = mock(DefaultAgent.class);

    final Instance<AgentBuilder<?, ?>> allBuilders = mock(Instance.class);
    when(allBuilders.stream()).thenReturn(Stream.of(defaultBuilder, orchestratorBuilder, workerBuilder));

    final AgentProvider provider = new AgentProvider(allBuilders);
    final AgentContext context = mock(AgentContext.class);

    final AgentConfig orchestratorConfig = new AgentConfig(AgentConfig.AgentType.ORCHESTRATOR);
    when(orchestratorBuilder.build(orchestratorConfig, context)).thenReturn(orchestratorAgent);
    assertSame(orchestratorAgent, provider.get(orchestratorConfig, context));

    final AgentConfig workerConfig = new AgentConfig(AgentConfig.AgentType.WORKER);
    when(workerBuilder.build(workerConfig, context)).thenReturn(workerAgent);
    assertSame(workerAgent, provider.get(workerConfig, context));
  }
}

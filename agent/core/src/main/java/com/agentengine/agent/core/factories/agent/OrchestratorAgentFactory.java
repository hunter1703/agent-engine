package com.agentengine.agent.core.factories.agent;

import com.agentengine.agent.core.tools.agent.AwaitAgentTool;
import com.agentengine.agent.core.tools.agent.SendMessageTool;
import com.agentengine.agent.core.tools.agent.SpawnAgentTool;
import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.agents.DelegatedAgent;
import com.agentengine.agent.infra.agents.ParallelOrchestratorAgent;
import com.agentengine.agent.infra.factories.agent.AbstractAgentFactory;
import com.agentengine.agent.infra.factories.agent.AgentProvider;
import com.agentengine.agent.infra.factories.agent.builders.BaseLlmAgentBuilder;
import com.agentengine.agent.infra.factories.agent.builders.ParallelOrchestratorAgentBuilder;
import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.agent.infra.tools.ToolFactory;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.OrchestrationMode;
import com.agentengine.util.agents.beans.config.OrchestratorAgentConfig;
import com.agentengine.util.agents.beans.config.OrchestratorParallelConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.pekko.ActorSystemProvider;
import io.quarkus.arc.WithCaching;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class OrchestratorAgentFactory extends AbstractAgentFactory<OrchestratorAgentConfig, Agent> {
  private final AgentService agentService;
  protected final Instance<AgentProvider> agentProviderInstance;
  private final ActorSystemProvider actorSystemProvider;

  @Inject
  public OrchestratorAgentFactory(
      final ModelProvider modelProvider,
      final ToolFactory toolFactory,
      @WithCaching final Instance<AgentProvider> agentProviderInstance,
      final AgentService agentService,
      final ActorSystemProvider actorSystemProvider) {
    super(modelProvider, toolFactory);
    this.agentService = agentService;
    this.agentProviderInstance = agentProviderInstance;
    this.actorSystemProvider = actorSystemProvider;
  }

  @Override
  public Agent build(final OrchestratorAgentConfig config) {
    OrchestrationMode mode = config.orchestrationModeEnum();
    if (mode == OrchestrationMode.UNKNOWN) {
      mode = OrchestrationMode.MANAGER;
    }
    return switch (mode) {
      case MANAGER -> buildManager(config);
      case PARALLEL -> buildParallel(config);
      default ->
          throw new IllegalArgumentException(
              "Unsupported orchestration mode: " + mode + " for agent_id=" + config.getId());
    };
  }

  private List<? extends Agent> buildSubAgents(final List<String> subAgentIds) {
    if (CollectionUtils.isEmpty(subAgentIds)) {
      return List.of();
    }
    final List<Agent> subAgents = new ArrayList<>();
    for (final String subAgentId : subAgentIds) {
      if (StringUtils.isBlank(subAgentId)) {
        continue;
      }
      final BaseAgentConfig subAgent = agentService.getAgent(subAgentId);
      if (subAgent == null) {
        throw new IllegalArgumentException("Sub agent config missing for id: " + subAgentId);
      }
      subAgents.add(agentProviderInstance.get().create(subAgent));
    }
    return subAgents;
  }

  private ParallelOrchestratorAgent buildParallel(final OrchestratorAgentConfig config) {
    final List<? extends Agent> subAgents = buildSubAgents(config.getSubAgentIds());
    if (CollectionUtils.isEmpty(subAgents)) {
      throw new IllegalArgumentException(
          "orchestrator mode PARALLEL requires non-empty subAgentIds for agent_id="
              + config.getId());
    }
    final OrchestratorParallelConfig parallel = config.getParallel();
    return new ParallelOrchestratorAgentBuilder()
        .subAgents(subAgents)
        .aggregationPolicy(parallel.aggregationPolicyEnum())
        .stoppingPolicy(parallel.stoppingPolicyEnum())
        .quorum(parallel.getQuorum())
        .agentConfig(config)
        .build();
  }

  private DelegatedAgent buildManager(final OrchestratorAgentConfig config) {
    final List<String> subAgentIds = CollectionUtils.nullSafeList(config.getSubAgentIds());
    final BaseLlmAgentBuilder builder = createLlmAgentBuilder(config);
    builder.appendTools(
        List.of(
            new SpawnAgentTool(actorSystemProvider, subAgentIds),
            new SendMessageTool(actorSystemProvider),
            new AwaitAgentTool(actorSystemProvider)));

    final List<? extends Agent> transferableSubAgents =
        buildSubAgents(config.getTransferableSubAgentIds());
    if (CollectionUtils.isNotEmpty(transferableSubAgents)) {
      builder.subAgents(transferableSubAgents);
    }
    return builder.build();
  }

  @Override
  public String type() {
    return BaseAgentConfig.AgentType.ORCHESTRATOR.type();
  }
}

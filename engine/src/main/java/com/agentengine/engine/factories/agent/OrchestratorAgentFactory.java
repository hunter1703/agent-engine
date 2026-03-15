package com.agentengine.engine.factories.agent;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.agents.ParallelOrchestratorAgent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.builders.agent.BaseLlmAgentBuilder;
import com.agentengine.engine.builders.agent.ParallelOrchestratorAgentBuilder;
import com.agentengine.engine.builders.agent.SequentialAgentBuilder;
import com.agentengine.engine.factories.model.ModelProvider;
import com.agentengine.engine.plugin.Agent;
import com.agentengine.engine.tools.ToolFactory;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.tools.AgentTool;
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

  @Inject
  public OrchestratorAgentFactory(final ModelProvider modelProvider, final ToolFactory toolFactory,
      @WithCaching final Instance<AgentProvider> agentProviderInstance, final AgentService agentService) {
    super(modelProvider, toolFactory);
    this.agentService = agentService;
    this.agentProviderInstance = agentProviderInstance;
  }

  @Override
  public Agent build(final OrchestratorAgentConfig config) {
    final List<? extends Agent> subAgents = buildSubAgents(config.getSubAgentIds());
    OrchestrationMode mode = config.orchestrationModeEnum();
    if (mode == OrchestrationMode.UNKNOWN) {
      mode = OrchestrationMode.TRANSFER;
    }
    return switch (mode) {
      case MANAGER -> buildManager(config, subAgents);
      case SEQUENTIAL -> buildSequential(config, subAgents);
      case PARALLEL -> buildParallel(config, subAgents);
      case TRANSFER -> buildTransfer(config, subAgents);
      default -> throw new IllegalStateException("Unexpected value: " + mode);
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
      final BaseAgentConfig subAgent = agentService.getAgent(subAgentId)
          .orElseThrow(() -> new IllegalArgumentException("Sub agent not found for id: " + subAgentId));
      subAgents.add(agentProviderInstance.get().create(subAgent));
    }
    return subAgents;
  }

  private DelegatedAgent buildSequential(final BaseAgentConfig config, final List<? extends Agent> subAgents) {
    if (CollectionUtils.isEmpty(subAgents)) {
      throw new IllegalArgumentException("orchestrator mode SEQUENTIAL requires non-empty subAgentIds for agent_id=" + config.getId());
    }
    return new SequentialAgentBuilder().agentConfig(config).subAgents(subAgents).build();
  }

  private static ParallelOrchestratorAgent buildParallel(final OrchestratorAgentConfig config, final List<? extends Agent> subAgents) {
    final OrchestratorParallelConfig parallel = config.getParallel();
    return new ParallelOrchestratorAgentBuilder().subAgents(subAgents).aggregationPolicy(parallel.aggregationPolicyEnum())
        .stoppingPolicy(parallel.stoppingPolicyEnum()).quorum(parallel.getQuorum()).agentConfig(config).build();
  }

  private DelegatedAgent buildManager(final BaseAgentConfig config, final List<? extends Agent> subAgents) {
    final BaseLlmAgentBuilder builder = createLlmAgentBuilder(config);
    if (CollectionUtils.isNotEmpty(subAgents)) {
      for (final BaseAgent subAgent : subAgents) {
        builder.appendTools(List.of(AgentTool.create(subAgent)));
      }
    }
    return builder.build();
  }

  private DelegatedAgent buildTransfer(final BaseAgentConfig config, final List<? extends Agent> subAgents) {
    final BaseLlmAgentBuilder builder = createLlmAgentBuilder(config);
    if (CollectionUtils.isNotEmpty(subAgents)) {
      builder.subAgents(subAgents);
    }
    return builder.build();
  }

  @Override
  public String type() {
    return BaseAgentConfig.AgentType.ORCHESTRATOR.type();
  }
}

package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.ParallelOrchestratorAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.util.StringUtils;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.engine.tools.ToolRegistry;
import com.google.adk.agents.BaseAgent;
import com.google.adk.tools.AgentTool;
import com.google.adk.tools.BaseTool;
import io.quarkus.arc.WithCaching;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class OrchestratorAgentBuilder extends AbstractAgentBuilder<OrchestratorAgentConfig, Agent> {
  private final AgentService agentService;
  @Inject
  public OrchestratorAgentBuilder(
      final ModelProvider modelProvider,
      final SessionService sessionService,
      final ToolRegistry toolRegistry,
      final ModelRepository modelRepository,
      @WithCaching Instance<AgentProvider> agentProvider,
      final AgentService agentService) {
    super(modelProvider, sessionService, toolRegistry, modelRepository, agentProvider);
    this.agentService = agentService;
  }

  @Override
  public Agent build(final OrchestratorAgentConfig config) {
    final List<? extends Agent> subAgents = buildSubAgents(config.getSubAgentIds());
    OrchestrationMode mode = config.getOrchestrationMode();
    mode = mode == null ? OrchestrationMode.TRANSFER : mode;
    return switch (mode) {
      case SEQUENTIAL -> buildSequential(config, subAgents);
      case PARALLEL -> buildParallel(config, subAgents);
      case TRANSFER, UNKNOWN -> buildTransfer(config, subAgents);
    };
  }

  private List<? extends DelegatedAgent> buildSubAgents(final List<String> subAgentIds) {
    if (CollectionUtils.isEmpty(subAgentIds)) {
      return List.of();
    }
    final List<DelegatedAgent> subAgents = new ArrayList<>();
    for (final String subAgentId : subAgentIds) {
      if (StringUtils.isBlank(subAgentId)) {
        continue;
      }
      final BaseAgentConfig subAgent = agentService.getAgent(subAgentId).orElseThrow(() -> new IllegalArgumentException("Sub agent not found for id: " + subAgentId));
        subAgents.add(agentProvider.get().get(subAgent));
    }
    return subAgents;
  }

  private DelegatedAgent buildTransfer(final BaseAgentConfig config, final List<? extends Agent> subAgents) {
    final DefaultLLMAgentBuilder builder = getBuilder(config);
    if (CollectionUtils.isNotEmpty(subAgents)) {
      builder.subAgents(subAgents);
      final List<BaseTool> subAgentTools = new ArrayList<>();
      for (final BaseAgent subAgent : subAgents) {
        if (subAgent == null || StringUtils.isBlank(subAgent.name())) {
          continue;
        }
        subAgentTools.add(AgentTool.create(subAgent));
      }
      builder.appendTools(subAgentTools);
    }
    return builder.build();
  }

  private DelegatedAgent buildSequential(final BaseAgentConfig config, final List<? extends Agent> subAgents) {
    if (CollectionUtils.isEmpty(subAgents)) {
      throw new IllegalArgumentException(
          "orchestrator mode SEQUENTIAL requires non-empty subAgentIds for agent_id="
              + config.getId());
    }
    return new SequentialAgentBuilder().agentConfig(config).subAgents(subAgents).build();
  }

  private static ParallelOrchestratorAgent buildParallel(final OrchestratorAgentConfig config, final List<? extends Agent> subAgents) {
    final OrchestratorParallelConfig parallel = config.getParallel();
    return new ParallelOrchestratorAgentBuilder()
        .subAgents(subAgents)
        .aggregationPolicy(parallel.getAggregationPolicy())
        .stoppingPolicy(parallel.getStoppingPolicy())
        .quorum(parallel.getQuorum()).agentConfig(config)
        .build();
  }

  @Override
  public String type() {
    return BaseAgentConfig.AgentType.ORCHESTRATOR.name().toLowerCase();
  }
}

package com.agentengine.engine.validation;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.beans.config.ParallelAggregationPolicy;
import com.agentengine.engine.api.beans.config.ParallelStoppingPolicy;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import jakarta.inject.Singleton;

@Singleton
public class AgentConfigParallelRuleValidator implements ConfigRuleValidator<BaseAgentConfig> {

  @Override
  public Class<BaseAgentConfig> targetType() {
    return BaseAgentConfig.class;
  }

  @Override
  public int order() {
    return 30;
  }

  @Override
  public void validate(final BaseAgentConfig config, final ValidationCollector errors) {
    final BaseAgentConfig.AgentType type =
        BaseAgentConfig.AgentType.valueOfOrDefault(config.getType());
    final OrchestrationMode orchestrationMode = resolveMode(config);
    if (type != BaseAgentConfig.AgentType.ORCHESTRATOR
        || orchestrationMode != OrchestrationMode.PARALLEL
        || !(config instanceof OrchestratorAgentConfig orchestratorConfig)) {
      return;
    }

    final OrchestratorParallelConfig parallel = orchestratorConfig.getParallel();
    if (parallel == null) {
      return;
    }
    final ParallelAggregationPolicy aggregationPolicy =
        parallel.getAggregationPolicy() == null
            ? ParallelAggregationPolicy.CONCATENATE
            : parallel.getAggregationPolicy();
    final ParallelStoppingPolicy stoppingPolicy =
        parallel.getStoppingPolicy() == null
            ? ParallelStoppingPolicy.ALL_COMPLETE
            : parallel.getStoppingPolicy();
    if (aggregationPolicy == ParallelAggregationPolicy.UNKNOWN
        || stoppingPolicy == ParallelStoppingPolicy.UNKNOWN) {
      return;
    }

    final int quorum = Math.max(1, parallel.getQuorum());
    if (stoppingPolicy != ParallelStoppingPolicy.QUORUM) {
      return;
    }

    final int subAgentCount =
        (int)
            CollectionUtils.nullSafeList(config.getSubAgentIds()).stream()
                .filter(StringUtils::isNotBlank)
                .count();
    if (quorum > subAgentCount) {
      errors.add(
          "orchestrator mode parallel with stoppingPolicy=QUORUM requires quorum <= sub-agent"
              + " count; requested quorum="
              + quorum
              + " subAgentCount="
              + subAgentCount
              + " agent_id="
              + config.getId());
    }
  }

  private static OrchestrationMode resolveMode(final BaseAgentConfig config) {
    if (!(config instanceof OrchestratorAgentConfig orchestratorConfig)) {
      return OrchestrationMode.TRANSFER;
    }
    final OrchestrationMode mode = orchestratorConfig.getOrchestrationMode();
    return mode == null ? OrchestrationMode.TRANSFER : mode;
  }
}

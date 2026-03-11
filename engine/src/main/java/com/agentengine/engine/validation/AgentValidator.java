package com.agentengine.engine.validation;

import com.agentengine.engine.api.beans.config.*;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.validation.ValidationCollector;
import com.agentengine.util.common.validation.Validator;
import dev.langchain4j.store.embedding.filter.logical.Or;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

@Singleton
public class AgentValidator implements Validator<BaseAgentConfig> {

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
    if (requiresModelId(config) && StringUtils.isBlank(config.getModelId())) {
      errors.add("Agent type and modelId are required");
    }

    if (config instanceof OrchestratorAgentConfig orchestratorAgentConfig) {
      final List<String> subAgentIds = config.getSubAgentIds();
      if (CollectionUtils.isEmpty(subAgentIds)) {
        errors.add("orchestrator agent requires non-empty subAgentIds; agent_id=" + config.getId());
      }
      final OrchestratorParallelConfig parallel = orchestratorAgentConfig.getParallel();
      if (orchestratorAgentConfig.orchestrationModeEnum() == OrchestrationMode.PARALLEL
          && parallel.stoppingPolicyEnum() == ParallelStoppingPolicy.QUORUM
          && parallel.getQuorum() > subAgentIds.size()) {
        errors.add(
            "orchestrator mode parallel with stoppingPolicy=QUORUM requires quorum <= sub-agent"
                + " count; requested quorum="
                + parallel.getQuorum()
                + " subAgentCount="
                + subAgentIds.size()
                + " agent_id="
                + config.getId());
      }
    }
  }

  public static boolean requiresModelId(final BaseAgentConfig config) {
    if (config instanceof DefaultAgentConfig) {
      return true;
    }
    if (config instanceof OrchestratorAgentConfig orchestratorAgentConfig) {
      final OrchestrationMode orchestrationMode = orchestratorAgentConfig.orchestrationModeEnum();
      return orchestrationMode == OrchestrationMode.TRANSFER
          || orchestrationMode == OrchestrationMode.MANAGER;
    }
    return false;
  }
}

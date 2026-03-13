package com.agentengine.engine.utils;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.beans.config.ParallelStoppingPolicy;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.validation.ValidationCollector;
import com.agentengine.util.common.validation.Validator;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.function.Predicate;

@Singleton
public class AgentValidator implements Validator<BaseAgentConfig> {

  private final Predicate<String> subAgentExistsPredicate;

  public AgentValidator(Instance<AgentService> agentService) {
    this.subAgentExistsPredicate = id -> agentService.get().getAgent(id).isPresent();
  }

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
    if (config == null || errors == null) {
      return;
    }
    if (StringUtils.isBlank(config.getType())) {
      errors.add("Agent type is required");
    }
    if (config instanceof DefaultAgentConfig && StringUtils.isBlank(config.getModelId())) {
      errors.add("Agent type and modelId are required");
    }
    if (!(config instanceof OrchestratorAgentConfig) && CollectionUtils.isNotEmpty(config.getSubAgentIds())) {
      errors.add("subAgentIds are supported only for type=orchestrator; agent_id=" + config.getId());
    }

    if (config instanceof OrchestratorAgentConfig orchestratorAgentConfig) {
      final List<String> subAgentIds = config.getSubAgentIds();
      final OrchestrationMode orchestrationMode = orchestratorAgentConfig.orchestrationModeEnum();
      if ((orchestrationMode == OrchestrationMode.SEQUENTIAL || orchestrationMode == OrchestrationMode.PARALLEL)
          && CollectionUtils.isEmpty(subAgentIds)) {
        errors.add("orchestrator agent requires non-empty subAgentIds; agent_id=" + config.getId());
      }
      final OrchestratorParallelConfig parallel = orchestratorAgentConfig.getParallel();
      if (orchestrationMode == OrchestrationMode.PARALLEL && parallel != null
          && parallel.stoppingPolicyEnum() == ParallelStoppingPolicy.QUORUM && parallel.getQuorum() > subAgentIds.size()) {
        errors.add("orchestrator mode parallel with stoppingPolicy=QUORUM requires quorum <= sub-agent" + " count; requested quorum="
            + parallel.getQuorum() + " subAgentCount=" + subAgentIds.size() + " agent_id=" + config.getId());
      }

      validateOrchestratorSubAgentsExist(orchestratorAgentConfig, subAgentExistsPredicate, errors);
    }
  }

  public static void validateOrchestratorSubAgentsExist(final OrchestratorAgentConfig config, final Predicate<String> subAgentExists,
      final ValidationCollector errors) {
    if (config == null || errors == null || CollectionUtils.isEmpty(config.getSubAgentIds())) {
      return;
    }
    final List<String> missing = config.getSubAgentIds().stream().filter(StringUtils::isNotBlank)
        .filter(subAgentId -> subAgentExists == null || !subAgentExists.test(subAgentId)).toList();
    if (!missing.isEmpty()) {
      errors.add("Sub-agent(s) not found: " + String.join(", ", missing));
    }
  }
}

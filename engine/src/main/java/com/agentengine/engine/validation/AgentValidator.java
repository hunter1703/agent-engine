package com.agentengine.engine.validation;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.beans.config.ParallelAggregationPolicy;
import com.agentengine.engine.api.beans.config.ParallelStoppingPolicy;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.util.StringUtils;
import com.agentengine.util.validation.ValidationCollector;
import com.agentengine.util.validation.Validator;
import jakarta.inject.Singleton;
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
    validateRequiredTypeAndModelId(config, errors);
    validateSubAgentRules(config, errors);
    validateParallelQuorum(config, errors);
  }

  public static void validateRequiredTypeAndModelId(
      final BaseAgentConfig config, final ValidationCollector errors) {
    if (config == null || errors == null) {
      return;
    }
    if (StringUtils.isBlank(config.getType())) {
      errors.add("Agent type is required");
      return;
    }
    if (requiresModelId(config) && StringUtils.isBlank(config.getModelId())) {
      errors.add("Agent type and modelId are required");
    }
  }

  public static boolean requiresModelId(final BaseAgentConfig config) {
    return config == null || !"orchestrator".equalsIgnoreCase(config.getType());
  }

  public static void validateOrchestratorSubAgentsExist(
      final BaseAgentConfig config,
      final Predicate<String> subAgentExists,
      final ValidationCollector errors) {
    if (config == null
        || errors == null
        || subAgentExists == null
        || !"orchestrator".equalsIgnoreCase(config.getType())
        || config.getSubAgentIds() == null) {
      return;
    }
    final List<String> missingSubAgents =
        config.getSubAgentIds().stream()
            .filter(StringUtils::isNotBlank)
            .filter(subAgentId -> !subAgentExists.test(subAgentId))
            .toList();
    if (!missingSubAgents.isEmpty()) {
      errors.add("Sub-agent(s) not found: " + String.join(", ", missingSubAgents));
    }
  }

  private static void validateSubAgentRules(
      final BaseAgentConfig config, final ValidationCollector errors) {
    final BaseAgentConfig.AgentType type =
        BaseAgentConfig.AgentType.valueOfOrDefault(config.getType());
    final OrchestrationMode orchestrationMode = resolveMode(config);
    final List<String> subAgents =
        CollectionUtils.nullSafeList(config.getSubAgentIds()).stream()
            .filter(StringUtils::isNotBlank)
            .toList();

    if (type != BaseAgentConfig.AgentType.ORCHESTRATOR && !subAgents.isEmpty()) {
      errors.add("subAgentIds are supported only for type=orchestrator; agent_id=" + config.getId());
    }
    if (type == BaseAgentConfig.AgentType.ORCHESTRATOR
        && (orchestrationMode == OrchestrationMode.SEQUENTIAL
            || orchestrationMode == OrchestrationMode.PARALLEL)
        && subAgents.isEmpty()) {
      errors.add(
          "orchestrator mode "
              + orchestrationMode.name().toLowerCase()
              + " requires non-empty subAgentIds; agent_id="
              + config.getId());
    }
  }

  private static void validateParallelQuorum(
      final BaseAgentConfig config, final ValidationCollector errors) {
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

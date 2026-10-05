package com.agentengine.catalog.core.validation;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.DefaultAgentConfig;
import com.agentengine.util.agents.beans.config.OrchestrationMode;
import com.agentengine.util.agents.beans.config.OrchestratorAgentConfig;
import com.agentengine.util.agents.beans.config.OrchestratorParallelConfig;
import com.agentengine.util.agents.beans.config.ParallelStoppingPolicy;
import com.agentengine.util.agents.beans.config.ToolsConfig;
import com.agentengine.util.agents.beans.tools.ConnectorToolConfigsList;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.validation.ValidationCollector;
import com.agentengine.util.common.validation.Validator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import java.util.List;

@Singleton
public class AgentValidator implements Validator<BaseAgentConfig> {
  private static final String CONNECTORS_TOOL_NAME = "connectors";

  private final jakarta.validation.Validator beanValidator;

  @Inject
  public AgentValidator(final jakarta.validation.Validator beanValidator) {
    this.beanValidator = beanValidator;
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
    if (!(config instanceof OrchestratorAgentConfig)
        && CollectionUtils.isNotEmpty(config.getSubAgentIds())) {
      errors.add(
          "subAgentIds are supported only for type=orchestrator; agent_id=" + config.getId());
    }
    if (config instanceof OrchestratorAgentConfig orchestratorAgentConfig) {
      validateOrchestratorConfig(orchestratorAgentConfig, errors);
    }
    validateToolConfigs(config, errors);
  }

  private void validateOrchestratorConfig(
      final OrchestratorAgentConfig config, final ValidationCollector errors) {
    final List<String> subAgentIds = config.getSubAgentIds();
    final OrchestrationMode orchestrationMode = config.orchestrationModeEnum();
    if (CollectionUtils.isEmpty(subAgentIds)
        && CollectionUtils.isEmpty(config.getTransferableSubAgentIds())) {
      errors.add(
          "orchestrator agent requires non-empty subAgentIds or transferableSubAgentIds; agent_id="
              + config.getId());
    }
    final OrchestratorParallelConfig parallel = config.getParallel();
    if (orchestrationMode == OrchestrationMode.PARALLEL
        && parallel != null
        && parallel.stoppingPolicyEnum() == ParallelStoppingPolicy.QUORUM
        && parallel.getQuorum() > subAgentIds.size()) {
      errors.add(
          "orchestrator mode parallel with stoppingPolicy=QUORUM requires quorum <= sub-agent count; requested quorum="
              + parallel.getQuorum()
              + " subAgentCount="
              + subAgentIds.size()
              + " agent_id="
              + config.getId());
    }
  }

  private void validateToolConfigs(final BaseAgentConfig config, final ValidationCollector errors) {
    for (final ToolsConfig toolConfig : CollectionUtils.nullSafeList(config.getTools())) {
      if (!CONNECTORS_TOOL_NAME.equals(toolConfig.getToolName())) {
        continue;
      }
      final ConnectorToolConfigsList connectorConfigs =
          JsonUtils.fromMap(toolConfig.getConfigs(), ConnectorToolConfigsList.class);
      if (connectorConfigs == null) {
        continue;
      }
      for (final ConstraintViolation<ConnectorToolConfigsList> violation :
          beanValidator.validate(connectorConfigs)) {
        errors.add(
            CONNECTORS_TOOL_NAME
                + " tool config "
                + violation.getPropertyPath()
                + ": "
                + violation.getMessage());
      }
    }
  }
}

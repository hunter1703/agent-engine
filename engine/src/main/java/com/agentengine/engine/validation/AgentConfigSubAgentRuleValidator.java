package com.agentengine.engine.validation;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class AgentConfigSubAgentRuleValidator implements ConfigRuleValidator<BaseAgentConfig> {

  @Override
  public Class<BaseAgentConfig> targetType() {
    return BaseAgentConfig.class;
  }

  @Override
  public int order() {
    return 20;
  }

  @Override
  public void validate(final BaseAgentConfig config, final ValidationCollector errors) {
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

  private static OrchestrationMode resolveMode(final BaseAgentConfig config) {
    if (!(config instanceof OrchestratorAgentConfig orchestratorConfig)) {
      return OrchestrationMode.TRANSFER;
    }
    final OrchestrationMode mode = orchestratorConfig.getOrchestrationMode();
    return mode == null ? OrchestrationMode.TRANSFER : mode;
  }
}

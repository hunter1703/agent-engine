package com.agentengine.engine.validation;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestrationMode;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.utils.StringUtils;
import jakarta.inject.Singleton;

@Singleton
public class AgentConfigModelRuleValidator implements ConfigRuleValidator<BaseAgentConfig> {

  @Override
  public Class<BaseAgentConfig> targetType() {
    return BaseAgentConfig.class;
  }

  @Override
  public int order() {
    return 10;
  }

  @Override
  public void validate(final BaseAgentConfig config, final ValidationCollector errors) {
    final BaseAgentConfig.AgentType type =
        BaseAgentConfig.AgentType.valueOfOrDefault(config.getType());
    final OrchestrationMode orchestrationMode = resolveMode(config);
    final boolean modelRequired =
        type == BaseAgentConfig.AgentType.DEFAULT
            || type == BaseAgentConfig.AgentType.UNKNOWN
            || (type == BaseAgentConfig.AgentType.ORCHESTRATOR
                && (orchestrationMode == OrchestrationMode.TRANSFER
                    || orchestrationMode == OrchestrationMode.UNKNOWN));
    if (modelRequired && StringUtils.isBlank(config.getModelId())) {
      errors.add(
          "modelId is required for agent type="
              + type.name().toLowerCase()
              + " orchestrationMode="
              + orchestrationMode.name().toLowerCase());
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

package com.agentengine.engine.api.utils;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.CompactionContextStrategyConfig;
import com.agentengine.engine.api.beans.config.ContextStrategyConfig;

public final class AgentUtils {
  private AgentUtils() {}

  public static ContextStrategyConfig resolveContextStrategy(final BaseAgentConfig agentConfig) {
    if (agentConfig == null || agentConfig.getContextStrategy() == null) {
      return new CompactionContextStrategyConfig();
    }
    return agentConfig.getContextStrategy();
  }

  public static ContextStrategyConfig.ContextStrategyType resolveContextStrategyType(
      final BaseAgentConfig agentConfig) {
    final ContextStrategyConfig strategy = resolveContextStrategy(agentConfig);
    return ContextStrategyConfig.ContextStrategyType.valueOfOrDefault(strategy.getType());
  }
}

package com.agentengine.agent.infra.utils;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.InvocationContext;

public final class AgentUtils {
  private AgentUtils() {}

  public static ContextStrategyConfig resolveContextStrategy(final BaseAgentConfig agentConfig) {
    if (agentConfig == null || agentConfig.getContextStrategy() == null) {
      return new CompactionContextStrategyConfig();
    }
    return agentConfig.getContextStrategy();
  }

  static String getAgentIdFromContext(final InvocationContext context) {
    if (context == null || context.agent() == null || StringUtils.isBlank(context.agent().name())) {
      return "unknown";
    }
    return context.agent().name();
  }
}

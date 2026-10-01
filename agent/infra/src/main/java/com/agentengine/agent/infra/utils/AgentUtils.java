package com.agentengine.agent.infra.utils;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.InvocationContext;

public final class AgentUtils {
  private AgentUtils() {}

  public static ContextStrategyConfig resolveContextStrategy(final BaseAgentConfig agentConfig) {
    if (agentConfig == null || agentConfig.getContextStrategy() == null) {
      return new CompactionContextStrategyConfig();
    }
    return agentConfig.getContextStrategy();
  }

  public static String appName(final String agentId) {
    if (StringUtils.isBlank(agentId)) {
      return "_app";
    }
    return "_%s".formatted(agentId.replaceAll("[^a-zA-Z0-9_]", "_"));
  }

  static String getAgentIdFromContext(final InvocationContext context) {
    if (context == null) {
      return "unknown";
    }
    if (context.agent() != null && StringUtils.isNotBlank(context.agent().name())) {
      return context.agent().name();
    }
    if (context.session() != null && StringUtils.isNotBlank(context.session().appName())) {
      return context.session().appName();
    }
    return "unknown";
  }
}

package com.agentengine.runtime.utils;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;

public final class AgentUtils {
    private AgentUtils() {}

    public static ContextStrategyConfig resolveContextStrategy(final BaseAgentConfig agentConfig) {
        if (agentConfig == null || agentConfig.getContextStrategy() == null) {
            return new CompactionContextStrategyConfig();
        }
        return agentConfig.getContextStrategy();
    }
}

package com.agentengine.runtime.factories.context;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.runtime.context.ContextManager;

public interface ContextManagerFactory<C extends ContextStrategyConfig, CM extends ContextManager> {

  CM build(C contextConfig);

  default CM build(final C contextConfig, final BaseAgentConfig agentConfig) {
    return build(contextConfig);
  }

  String type();
}

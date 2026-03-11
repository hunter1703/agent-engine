package com.agentengine.engine.api.factories;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.ContextStrategyConfig;

public interface ContextManagerFactory<C extends ContextStrategyConfig, CM extends ContextManager> {

  CM build(C contextConfig);

  default CM build(final C contextConfig, final BaseAgentConfig agentConfig) {
    return build(contextConfig);
  }

  String type();
}

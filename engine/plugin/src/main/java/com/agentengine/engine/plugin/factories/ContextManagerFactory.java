package com.agentengine.engine.plugin.factories;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.ContextStrategyConfig;
import com.agentengine.engine.plugin.ContextManager;

public interface ContextManagerFactory<C extends ContextStrategyConfig, CM extends ContextManager> {

  CM build(C contextConfig);

  default CM build(final C contextConfig, final BaseAgentConfig agentConfig) {
    return build(contextConfig);
  }

  String type();
}

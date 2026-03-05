package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;

public interface ContextManagerBuilder<C extends ContextManagerConfig, CM extends ContextManager> {

  CM build(C contextConfig);

  default CM build(final C contextConfig, final AgentConfig agentConfig) {
    return build(contextConfig);
  }

  String type();
}

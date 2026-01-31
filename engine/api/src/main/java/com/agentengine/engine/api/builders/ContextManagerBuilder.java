package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;

public interface ContextManagerBuilder<C extends ContextManagerConfig, CM extends ContextManager> {

  CM build(C contextConfig);

  String type();
}

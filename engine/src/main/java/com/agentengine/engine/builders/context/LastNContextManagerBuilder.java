package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.beans.config.*;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.context.LastNContextManager;
import jakarta.inject.Singleton;

@Singleton
public class LastNContextManagerBuilder
    implements
        ContextManagerBuilder<LastNContextManagerConfig, LastNContextManager> {

  @Override
  public LastNContextManager build(final LastNContextManagerConfig contextConfig) {
    return new LastNContextManager(contextConfig.getKeepLast());
  }

  @Override
  public String type() {
    return ContextManagerConfig.ContextType.LAST_N.name().toLowerCase();
  }
}

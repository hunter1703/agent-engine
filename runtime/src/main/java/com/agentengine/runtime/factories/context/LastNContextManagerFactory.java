package com.agentengine.runtime.factories.context;

import com.agentengine.engine.api.beans.config.ContextStrategyConfig;
import com.agentengine.engine.api.beans.config.LastNContextStrategyConfig;
import com.agentengine.engine.context.LastNContextManager;
import com.agentengine.engine.plugin.factories.ContextManagerFactory;
import jakarta.inject.Singleton;

@Singleton
public class LastNContextManagerFactory implements ContextManagerFactory<LastNContextStrategyConfig, LastNContextManager> {

  @Override
  public LastNContextManager build(final LastNContextStrategyConfig contextConfig) {
    return new LastNContextManager(contextConfig.getKeepLastTokens());
  }

  @Override
  public String type() {
    return ContextStrategyConfig.ContextStrategyType.LAST_N.type();
  }
}

package com.agentengine.runtime.factories.context;

import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.agents.beans.config.LastNContextStrategyConfig;
import com.agentengine.runtime.context.LastNContextManager;
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

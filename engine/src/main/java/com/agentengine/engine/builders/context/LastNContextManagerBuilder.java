package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.beans.config.ContextStrategyConfig;
import com.agentengine.engine.api.beans.config.LastNContextStrategyConfig;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.context.LastNContextManager;
import jakarta.inject.Singleton;

@Singleton
public class LastNContextManagerBuilder
    implements ContextManagerBuilder<LastNContextStrategyConfig, LastNContextManager> {

  @Override
  public LastNContextManager build(final LastNContextStrategyConfig contextConfig) {
    return new LastNContextManager(contextConfig.getKeepLastTokens());
  }

  @Override
  public String type() {
    return ContextStrategyConfig.ContextStrategyType.LAST_N.name().toLowerCase();
  }
}

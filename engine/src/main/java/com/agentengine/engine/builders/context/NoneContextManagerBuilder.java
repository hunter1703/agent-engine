package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.beans.config.ContextStrategyConfig;
import com.agentengine.engine.api.beans.config.NoneContextStrategyConfig;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.context.NoOpContextManager;
import jakarta.inject.Singleton;

@Singleton
public class NoneContextManagerBuilder
    implements ContextManagerBuilder<NoneContextStrategyConfig, NoOpContextManager> {
  @Override
  public NoOpContextManager build(final NoneContextStrategyConfig contextConfig) {
    return NoOpContextManager.INSTANCE;
  }

  @Override
  public String type() {
    return ContextStrategyConfig.ContextStrategyType.NONE.name().toLowerCase();
  }
}

package com.agentengine.engine;

import com.agentengine.engine.ConfigRepository;
import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ModelConfig;

public final class NoopConfigRepository implements ConfigRepository {
  @Override
  public AgentConfig loadAgentConfig(final String agentName) {
    return null;
  }

  @Override
  public ModelConfig loadModelConfig(final String modelName) {
    return null;
  }
}

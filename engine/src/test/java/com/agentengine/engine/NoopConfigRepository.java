package com.agentengine.engine;

import com.agentengine.engine.client.ConfigRepository;
import com.agentengine.engine.client.beans.config.AgentConfig;
import com.agentengine.engine.client.beans.config.ModelConfig;

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

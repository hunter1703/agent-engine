package com.agentengine.engine;

import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;

public final class NoopConfigRepository implements ConfigRepository {
  @Override
  public AgentConfig loadAgentConfig(final String agentId) {
    return null;
  }

  @Override
  public ModelConfig loadModelConfig(final String modelId) {
    return null;
  }
}

package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
public interface ConfigRepository {
  AgentConfig loadAgentConfig(String agentId);

  ModelConfig loadModelConfig(String modelId);
}

package com.agentengine.engine.client;

import com.agentengine.engine.client.beans.config.AgentConfig;
import com.agentengine.engine.client.beans.config.ModelConfig;
public interface ConfigRepository {
  AgentConfig loadAgentConfig(String agentName);

  ModelConfig loadModelConfig(String modelName);
}

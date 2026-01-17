package com.agentengine.interfaces;

import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ModelConfig;

public interface ConfigRepository {
  AgentConfig loadAgentConfig(String agentName);

  ModelConfig loadModelConfig(String modelName);
}

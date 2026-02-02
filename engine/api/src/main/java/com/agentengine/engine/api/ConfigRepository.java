package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import java.util.List;

public interface ConfigRepository {
  AgentConfig loadAgentConfig(String agentId);

  ModelConfig loadModelConfig(String modelId);

  List<String> listAgentConfigs();
}

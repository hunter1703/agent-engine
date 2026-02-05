package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.google.adk.models.BaseLlm;

public interface ModelBuilder<L extends BaseLlm> {
  L build(String agentId, AgentModelConfig config, ModelConfig modelConfig);

  String type();
}

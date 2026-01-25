package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.AgentModelConfig;

public interface ModelBuilder<L extends LLMModel> {
  L build(String agentId, AgentModelConfig config);

  default L build(String agentId, AgentModelConfig config, MessageStore messageStore) {
    return build(agentId, config);
  }

  String type();
}

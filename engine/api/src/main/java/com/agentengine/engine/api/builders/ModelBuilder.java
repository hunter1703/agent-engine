package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.beans.config.AgentModelConfig;

public interface ModelBuilder<L extends LLMModel> {
    L build(String agentName, AgentModelConfig config);

    String type();
}

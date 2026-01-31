package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.google.adk.agents.LlmAgent;

public interface AgentBuilder<C extends AgentConfig, A extends LlmAgent> {

  A build(C agentConfig);

  String type();
}

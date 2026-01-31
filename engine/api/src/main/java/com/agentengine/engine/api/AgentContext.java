package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.google.adk.sessions.BaseSessionService;

public record AgentContext(AgentConfig agentConfig, BaseSessionService sessionService) {

  public String agentId() {
    return agentConfig == null ? null : agentConfig.getAgentId();
  }

  public AgentModelConfig modelConfig() {
    return agentConfig == null ? null : agentConfig.getModel();
  }
}

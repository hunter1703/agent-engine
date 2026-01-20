package com.agentengine.api.handlers;

import com.agentengine.engine.client.AgentRequest;
import com.agentengine.engine.client.AgentEngine;
import com.agentengine.interfaces.AgentManager;

public abstract class AbstractAgentRequestHandler implements AgentRequestHandler {

  private final AgentManager agentManager;

  public AbstractAgentRequestHandler(AgentManager agentManager) {
    this.agentManager = agentManager;
  }

  protected AgentEngine getOrCreateEngine(final AgentRequest request) {
    return agentManager.getOrStartEngine(request.getAgentName(), request.getAgentConfigPath());
  }
}

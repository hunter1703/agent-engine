package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.Agent;
import com.agentengine.interfaces.rest.services.AgentManager;

public abstract class AbstractAgentRequestHandler<T> implements AgentRequestHandler<T> {

  private final AgentManager agentManager;

  public AbstractAgentRequestHandler(AgentManager agentManager) {
    this.agentManager = agentManager;
  }

  protected Agent getOrCreateEngine(final AgentRequest request) {
    return agentManager.getOrStartEngine(request.getAgentName(), request.getAgentConfigPath());
  }
}

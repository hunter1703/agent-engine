package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.Agent;
import com.agentengine.interfaces.rest.services.AGUIAgent;
import com.agentengine.interfaces.rest.services.AgentManager;

public abstract class AbstractAgentRequestHandler implements AgentRequestHandler {

  private final AgentManager agentManager;

  public AbstractAgentRequestHandler(AgentManager agentManager) {
    this.agentManager = agentManager;
  }

  protected AGUIAgent getOrCreateEngine(final AgentRequest request) {
    return agentManager.getOrStartEngine(request.getAgentName(), request.getAgentConfigPath());
  }
}

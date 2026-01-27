package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.Agent;
import com.agentengine.interfaces.rest.services.AgentManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAgentRequestHandler<T> implements AgentRequestHandler<T> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractAgentRequestHandler.class);

  private final AgentManager agentManager;

  public AbstractAgentRequestHandler(AgentManager agentManager) {
    this.agentManager = agentManager;
  }

  protected Agent getOrCreateEngine(final AgentRequest request) {
    LOG.debug("Getting or creating agent engine - agent_id={} config_path=\"{}\"", request.getAgentId(),
        request.getAgentConfigPath());
    return agentManager.getOrStartEngine(request.getAgentId(), request.getAgentConfigPath());
  }
}

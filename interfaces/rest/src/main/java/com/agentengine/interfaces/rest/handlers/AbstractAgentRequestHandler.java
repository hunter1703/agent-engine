package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.agents.AgentSessionRuntime;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAgentRequestHandler<T> implements AgentRequestHandler<T> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractAgentRequestHandler.class);

  private final AgentSessionRuntimeManager agentManager;
  protected final AgentRunner agentRunner;

  public AbstractAgentRequestHandler(AgentSessionRuntimeManager agentManager, AgentRunner agentRunner) {
    this.agentManager = agentManager;
    this.agentRunner = agentRunner;
  }

  protected AgentSessionRuntime getOrCreateRuntime(final AgentRequest request) {
    LOG.debug("Getting or creating agent engine - agent_id={} config_path=\"{}\"", request.getAgentId(),
        request.getAgentConfigPath());
    LOG.trace("Agent engine request details - agent_id={} type={} config_path=\"{}\" session_id={}",
        request.getAgentId(), request.getType(), request.getAgentConfigPath(), request.getSessionId());
    return agentManager.getOrStartRuntime(request.getAgentId(), request.getSessionId());
  }
}

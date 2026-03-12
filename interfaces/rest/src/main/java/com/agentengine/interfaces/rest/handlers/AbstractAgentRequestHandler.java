package com.agentengine.interfaces.rest.handlers;

import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.engine.api.services.AgentExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAgentRequestHandler<Request extends AgentRequest, Response> implements AgentRequestHandler<Request, Response> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractAgentRequestHandler.class);

  protected AgentExecutionService agentExecutionService;

  public AbstractAgentRequestHandler(AgentExecutionService agentExecutionService) {
    this.agentExecutionService = agentExecutionService;
  }
}

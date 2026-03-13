package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;

public abstract class AbstractAgentRequestHandler<Request extends AgentRequest, Response>
    implements
      AgentRequestHandler<Request, Response> {

  private final AgentExecutionService agentExecutionService;

  protected AbstractAgentRequestHandler(final AgentExecutionService agentExecutionService) {
    this.agentExecutionService = agentExecutionService;
  }

  protected final AgentExecutionService agentExecutionService() {
    return agentExecutionService;
  }
}

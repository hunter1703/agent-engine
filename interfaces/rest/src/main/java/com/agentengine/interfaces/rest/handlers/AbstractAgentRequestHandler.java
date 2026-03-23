package com.agentengine.interfaces.rest.handlers;

import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.util.ms.MicroServiceClientProvider;

public abstract class AbstractAgentRequestHandler<Request extends AgentRequest, Response>
    implements AgentRequestHandler<Request, Response> {

  private final AgentExecutionService agentExecutionService;

  protected AbstractAgentRequestHandler(final MicroServiceClientProvider microServiceClientProvider) {
    this.agentExecutionService = microServiceClientProvider.get(AgentExecutionService.class);
  }

  protected final AgentExecutionService agentExecutionService() {
    return agentExecutionService;
  }
}

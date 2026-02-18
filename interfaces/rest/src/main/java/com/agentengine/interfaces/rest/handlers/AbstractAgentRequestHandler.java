package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.services.AgentExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAgentRequestHandler<T> implements AgentRequestHandler<T> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractAgentRequestHandler.class);

  protected final AgentExecutionService agentExecutionService;

  public AbstractAgentRequestHandler(AgentExecutionService agentExecutionService) {
    this.agentExecutionService = agentExecutionService;
  }
}

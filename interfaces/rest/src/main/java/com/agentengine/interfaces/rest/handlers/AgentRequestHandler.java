package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;

public interface AgentRequestHandler<T> {

  RequestType requestType();

  T handle(AgentRequest request);
}

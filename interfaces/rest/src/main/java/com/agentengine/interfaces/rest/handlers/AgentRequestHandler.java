package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;

public interface AgentRequestHandler {

  RequestType requestType();

  <T> T handle(AgentRequest request);
}

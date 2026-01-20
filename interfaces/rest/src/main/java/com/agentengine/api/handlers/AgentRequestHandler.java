package com.agentengine.api.handlers;

import com.agentengine.engine.client.AgentRequest;
import com.agentengine.engine.client.AgentRequest.RequestType;

public interface AgentRequestHandler {

  RequestType requestType();

  <T> T handle(AgentRequest request);
}

package com.agentengine.api.handlers;

import com.agentengine.api.AgentResponse;
import com.agentengine.client.AgentRequest;
import com.agentengine.client.AgentRequest.RequestType;
import com.agentengine.engine.AgentEngine;

public interface AgentRequestHandler {

  RequestType requestType();

  <T> T handle(AgentRequest request);
}

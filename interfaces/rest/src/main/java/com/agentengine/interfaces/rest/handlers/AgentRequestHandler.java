package com.agentengine.interfaces.rest.handlers;

import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.AgentRequest.RequestType;

public interface AgentRequestHandler<Request extends AgentRequest, Response> {

  RequestType requestType();

  Response handle(Request request);
}

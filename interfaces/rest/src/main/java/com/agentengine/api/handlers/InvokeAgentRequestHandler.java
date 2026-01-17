package com.agentengine.api.handlers;

import com.agentengine.api.AgentResponse;
import com.agentengine.api.InvokeResponse;
import com.agentengine.api.handlers.AgentRequestHandler;
import com.agentengine.client.AgentRequest;
import com.agentengine.client.AgentRequest.RequestType;
import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.message.Message;
import com.agentengine.interfaces.AgentService;
import jakarta.inject.Singleton;

@Singleton
public class InvokeAgentRequestHandler extends AbstractAgentRequestHandler {

  public InvokeAgentRequestHandler(final AgentService agentService) {
    super(agentService);
  }

  @Override
  public RequestType requestType() {
    return RequestType.INVOKE_AGENT;
  }

  @Override
  public AgentResponse handle(final AgentRequest request) {
    final AgentEngine engine = getOrCreateEngine(request);
    final String sessionId = request.getSessionId();
    Message response = engine.invoke(sessionId, Message.user(request.getMessage()));
    return new InvokeResponse(
        sessionId,
        response == null ? null : response.getContent(),
        response == null ? null : response.getThoughts());
  }
}

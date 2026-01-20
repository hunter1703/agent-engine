package com.agentengine.api.handlers;

import com.agentengine.api.AgentResponse;
import com.agentengine.api.InvokeResponse;
import com.agentengine.engine.client.AgentRequest;
import com.agentengine.engine.client.AgentRequest.RequestType;
import com.agentengine.engine.client.AgentEngine;
import com.agentengine.engine.client.beans.session.Message;
import com.agentengine.interfaces.AgentManager;
import jakarta.inject.Singleton;

@Singleton
public class InvokeAgentRequestHandler extends AbstractAgentRequestHandler {

  public InvokeAgentRequestHandler(final AgentManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.INVOKE_AGENT;
  }

  @SuppressWarnings("unchecked")
  @Override
  public AgentResponse handle(final AgentRequest request) {
    final AgentEngine engine = getOrCreateEngine(request);
    final String sessionId = request.getSessionId();
    Message response = engine.invoke(sessionId, Message.user(request.getMessage()));
    return new InvokeResponse(sessionId, response == null ? null : response.getContent(),
        response == null ? null : response.getThoughts());
  }
}

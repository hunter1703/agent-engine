package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.interfaces.rest.services.AgentManager;
import com.agui.core.event.BaseEvent;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Singleton;

import com.agentengine.engine.api.Agent;

@Singleton
public class StreamingInvokeAgentRequestHandler extends AbstractAgentRequestHandler<Multi<BaseEvent>> {

  public StreamingInvokeAgentRequestHandler(final AgentManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAMING_INVOKE_AGENT;
  }

  @Override
  public Multi<BaseEvent> handle(final AgentRequest request) {
    final Agent engine = getOrCreateEngine(request);
    final String sessionId = request.getSessionId();
    return Multi.createFrom().emitter(emitter -> {
      final AGUIEventEmitter listener = new AGUIEventEmitter(sessionId, request.getMessage(), emitter::emit);
      try {
        engine.invoke(sessionId, Message.user(request.getMessage()), listener);
      } finally {
        emitter.complete();
      }
    });
  }
}

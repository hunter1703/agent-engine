package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.client.AgentRequest;
import com.agentengine.engine.client.AgentRequest.RequestType;
import com.agentengine.engine.client.AgentEngine;
import com.agentengine.engine.events.AgentEvent;
import com.agentengine.engine.events.AgentEventAdapter;
import com.agentengine.engine.events.AgentEventPublisher;
import com.agentengine.engine.client.beans.session.Message;
import com.agentengine.interfaces.rest.services.AGUIAgent;
import com.agentengine.interfaces.rest.services.AGUISubscriber;
import com.agentengine.interfaces.rest.services.AgentManager;
import com.agui.core.agent.RunAgentParameters;
import com.agui.core.event.BaseEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class StreamingInvokeAgentRequestHandler extends AbstractAgentRequestHandler {

  public StreamingInvokeAgentRequestHandler(final AgentManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAMING_INVOKE_AGENT;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Multi<BaseEvent> handle(final AgentRequest request) {
    final AGUIAgent engine = getOrCreateEngine(request);
    final String sessionId = request.getSessionId();
    return Multi.createFrom().emitter(emitter -> {
      engine.run(sessionId, request.getMessage(), new SSESubscriber(emitter));
      emitter.complete();
    });
  }
}

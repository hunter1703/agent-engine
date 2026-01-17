package com.agentengine.api.handlers;

import com.agentengine.api.AgentResponse;
import com.agentengine.api.InvokeResponse;
import com.agentengine.client.AgentRequest;
import com.agentengine.client.AgentRequest.RequestType;
import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.events.AgentEvent;
import com.agentengine.engine.events.AgentEventAdapter;
import com.agentengine.engine.events.AgentEventPublisher;
import com.agentengine.engine.message.Message;
import com.agentengine.interfaces.AgentService;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class StreamingInvokeAgentRequestHandler extends AbstractAgentRequestHandler {

  public StreamingInvokeAgentRequestHandler(final AgentService agentService) {
    super(agentService);
  }

  @Override
  public RequestType requestType() {
    return RequestType.INVOKE_AGENT;
  }

  @Override
  public Multi<AgentEvent> handle(final AgentRequest request) {
    final AgentEngine engine = getOrCreateEngine(request);
    final String sessionId = request.getSessionId();
      return Multi.createFrom()
            .emitter(
                    emitter -> {
                      AtomicBoolean active = new AtomicBoolean(true);
                      emitter.emit(new AgentEvent("session", sessionId, Map.of("status", "ready")));
                      AgentEventPublisher publisher =
                              event -> {
                                if (active.get()) {
                                  emitter.emit(event);
                                }
                              };
                      engine.registerListener(sessionId, new AgentEventAdapter(publisher));
                      engine.invoke(sessionId, Message.user(request.getMessage()));
                      emitter.onTermination(
                              () -> {
                                active.set(false);
                                engine.unRegisterListener(sessionId);
                              });
                    });
  }
}

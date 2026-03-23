package com.agentengine.interfaces.rest.handlers;

import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.AgentRequest.RequestType;
import com.agentengine.util.ms.MicroServiceClientProvider;
import com.agui.core.event.BaseEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.reactivestreams.Publisher;

@Singleton
public class StreamAguiEventsRequestHandler extends AbstractAgentRequestHandler<AgentRequest, Publisher<BaseEvent>> {

  @Inject
  public StreamAguiEventsRequestHandler(final MicroServiceClientProvider clientProvider) {
    super(clientProvider);
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAM_AGUI_EVENTS;
  }

  @Override
  public Publisher<BaseEvent> handle(final AgentRequest request) {
    if (request.getSessionId() == null) {
      request.setSessionId(UUID.randomUUID().toString());
    }
    return agentExecutionService().run(request.getAgentId(), request.getSessionId(), request.getMessage());
  }
}

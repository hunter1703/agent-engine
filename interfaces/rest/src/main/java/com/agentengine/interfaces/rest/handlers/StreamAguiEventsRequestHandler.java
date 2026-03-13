package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.agui.AGUIEventMapper;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.AgentRequest.RequestType;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.UUID;

@Singleton
public class StreamAguiEventsRequestHandler extends AbstractAgentRequestHandler<AgentRequest, Flowable<BaseEvent>> {

  @Inject
  public StreamAguiEventsRequestHandler(final AgentExecutionService agentExecutionService) {
    super(agentExecutionService);
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAM_AGUI_EVENTS;
  }

  @Override
  public Flowable<BaseEvent> handle(final AgentRequest request) {
    if (request.getSessionId() == null) {
      request.setSessionId(UUID.randomUUID().toString());
    }
    final AGUIEventMapper mapper = new AGUIEventMapper(request.getSessionId(), request.getAgentId());
    return mapper.map(agentExecutionService().run(request.getAgentId(), request.getSessionId(), request.getMessage()));
  }
}

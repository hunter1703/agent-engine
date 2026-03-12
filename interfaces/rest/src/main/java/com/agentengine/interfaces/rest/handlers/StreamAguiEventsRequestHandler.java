package com.agentengine.interfaces.rest.handlers;

import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.AgentRequest.RequestType;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StreamAguiEventsRequestHandler extends AbstractAgentRequestHandler<AgentRequest, Flowable<BaseEvent>> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(StreamAguiEventsRequestHandler.class);

  @Inject
  public StreamAguiEventsRequestHandler(AgentExecutionService agentExecutionService) {
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
    return mapper.map(agentExecutionService.run(request.getAgentId(), request.getSessionId(), request.getMessage()));
  }
}

package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StreamAguiEventsRequestHandler
    extends AbstractAgentRequestHandler<Flowable<BaseEvent>> {
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
    final AGUIEventMapper mapper =
        new AGUIEventMapper(request.getSessionId(), request.getAgentId());
    return agentExecutionService
        .run(request)
        .concatMap(
            event -> {
              try {
                return mapper.map(event);
              } catch (Exception e) {
                LOGGER.error(
                    "Error mapping event to AGUI event - session_id={} agent_id={} event={} error=\"{}\"",
                    request.getSessionId(),
                    request.getAgentId(),
                    event,
                    e.getMessage(),
                    e);
                return Flowable.error(e);
              }
            })
        .concatWith(Flowable.defer(mapper::onComplete))
        .onErrorResumeNext(mapper::onError);
  }
}

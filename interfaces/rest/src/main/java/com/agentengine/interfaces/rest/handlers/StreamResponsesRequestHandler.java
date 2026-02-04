package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.responses.dtos.BaseResponsesEventData;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Singleton;

@Singleton
public class StreamResponsesRequestHandler extends AbstractAgentRequestHandler<Flowable<BaseResponsesEventData>> {

  private final StreamAguiEventsRequestHandler eventsRequestHandler;

  public StreamResponsesRequestHandler(final AgentRuntimeManager agentManager,
      StreamAguiEventsRequestHandler eventsRequestHandler) {
    super(agentManager);
    this.eventsRequestHandler = eventsRequestHandler;
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAM_RESPONSES;
  }

  @Override
  public Flowable<BaseResponsesEventData> handle(final AgentRequest request) {
    final ResponsesEventMapper responsesEventMapper = new ResponsesEventMapper(request.getAgentId());
    return eventsRequestHandler.handle(request).concatMap(responsesEventMapper::map)
        .concatWith(Flowable.defer(responsesEventMapper::onComplete)).onErrorResumeNext(responsesEventMapper::onError);
  }
}

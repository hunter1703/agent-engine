package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.responses.dtos.BaseResponsesEventData;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StreamResponsesRequestHandler extends AbstractAgentRequestHandler<Flowable<BaseResponsesEventData>> {

  private final StreamAguiEventsRequestHandler eventsRequestHandler;

  @Inject
  public StreamResponsesRequestHandler(final AgentSessionRuntimeManager agentManager,
      StreamAguiEventsRequestHandler eventsRequestHandler, AgentRunner agentRunner) {
    super(agentManager, agentRunner);
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

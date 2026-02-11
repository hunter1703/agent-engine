package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.agents.AgentSessionRuntime;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StreamAguiEventsRequestHandler extends AbstractAgentRequestHandler<Flowable<BaseEvent>> {
  private static final Logger LOGGER = LoggerFactory.getLogger(StreamAguiEventsRequestHandler.class);

  @Inject
  public StreamAguiEventsRequestHandler(final AgentSessionRuntimeManager agentManager, final AgentRunner agentRunner) {
    super(agentManager, agentRunner);
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAM_AGUI_EVENTS;
  }

  @Override
  public Flowable<BaseEvent> handle(final AgentRequest request) {
    final AgentSessionRuntime runtime = getOrCreateRuntime(request);
    final AGUIEventMapper mapper = new AGUIEventMapper(runtime.sessionId(), request.getAgentId());
    return agentRunner.runStreaming(runtime, request.getMessage()).concatMap(event -> {
      try {
        return mapper.map(event);
      } catch (Exception e) {
        LOGGER.error("Error mapping event to AGUI event - session_id={} agent_id={} event={} error=\"{}\"",
            runtime.sessionId(), request.getAgentId(), event, e.getMessage(), e);
        return Flowable.error(e);
      }
    }).concatWith(Flowable.defer(mapper::onComplete)).onErrorResumeNext(mapper::onError);
  }
}

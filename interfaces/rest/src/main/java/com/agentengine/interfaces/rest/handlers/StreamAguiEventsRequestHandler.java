package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.agents.AgentSessionRuntime;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agui.core.event.BaseEvent;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StreamAguiEventsRequestHandler extends AbstractAgentRequestHandler<Flowable<BaseEvent>> {

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
    return agentRunner.runStreaming(runtime, request.getMessage()).concatMap(mapper::map).concatWith(Flowable.defer(mapper::onComplete))
        .onErrorResumeNext(mapper::onError);
  }
}

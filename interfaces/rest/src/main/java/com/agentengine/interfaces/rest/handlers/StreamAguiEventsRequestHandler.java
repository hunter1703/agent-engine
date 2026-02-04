package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.agui.core.event.BaseEvent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.RunConfig.StreamingMode;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Singleton;

@Singleton
public class StreamAguiEventsRequestHandler extends AbstractAgentRequestHandler<Flowable<BaseEvent>> {

  public StreamAguiEventsRequestHandler(final AgentRuntimeManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAM_AGUI_EVENTS;
  }

  @Override
  public Flowable<BaseEvent> handle(final AgentRequest request) {
    final AgentRuntime runtime = getOrCreateRuntime(request);
    final String sessionId = ensureSession(runtime, request.getSessionId());
    final String message = request.getMessage();
    final Content messageContent = Content.fromParts(Part.builder().text(message).build());
    final RunConfig runConfig = RunConfig.builder().setStreamingMode(StreamingMode.SSE).build();
    final Flowable<Event> events = runtime.runner().runAsync(AgentRuntimeManager.DEFAULT_USER_ID, sessionId,
        messageContent, runConfig);
    final AGUIEventMapper mapper = new AGUIEventMapper(sessionId, request.getAgentId());
    return events.concatMap(mapper::map).concatWith(Flowable.defer(mapper::onComplete))
        .onErrorResumeNext(mapper::onError);
  }
}

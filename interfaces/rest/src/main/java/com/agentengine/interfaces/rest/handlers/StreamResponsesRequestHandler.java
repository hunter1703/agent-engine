package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.responses.dtos.BaseResponsesEventData;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agui.core.event.BaseEvent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.RunConfig.StreamingMode;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Singleton;

@Singleton
public class StreamResponsesRequestHandler extends AbstractAgentRequestHandler<Flowable<BaseResponsesEventData>> {

  public StreamResponsesRequestHandler(final AgentRuntimeManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAM_RESPONSES;
  }

  @Override
  public Flowable<BaseResponsesEventData> handle(final AgentRequest request) {
    final AgentRuntime runtime = getOrCreateRuntime(request);
    final String sessionId = ensureSession(runtime, request.getSessionId());
    final String message = request.getMessage();
    final Content messageContent = Content.fromParts(Part.builder().text(message).build());
    final RunConfig runConfig = RunConfig.builder().setStreamingMode(StreamingMode.SSE).build();
    final Flowable<Event> events = runtime.runner().runAsync(AgentRuntimeManager.DEFAULT_USER_ID, sessionId,
        messageContent, runConfig);
    final AGUIEventMapper aguiMapper = new AGUIEventMapper(sessionId, request.getAgentId());
    final ResponsesEventMapper responsesEventMapper = new ResponsesEventMapper(request.getAgentId());

    final Flowable<BaseEvent> aguiEvents = events
        .concatMap(aguiMapper::map)
        .concatWith(Flowable.defer(aguiMapper::onComplete))
        .onErrorResumeNext(aguiMapper::onError);

    return aguiEvents
        .concatMap(responsesEventMapper::map)
        .concatWith(Flowable.defer(responsesEventMapper::onComplete))
        .onErrorResumeNext(responsesEventMapper::onError);
  }
}

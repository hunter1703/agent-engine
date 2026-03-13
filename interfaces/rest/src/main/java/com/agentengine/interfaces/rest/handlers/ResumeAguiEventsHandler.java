package com.agentengine.interfaces.rest.handlers;

import static com.agentengine.interfaces.rest.dto.AgentRequest.RequestType.RESUME_SESSION;

import com.agentengine.engine.agui.AGUIEventMapper;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.ResumeSessionRequest;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Singleton;

@Singleton
public final class ResumeAguiEventsHandler extends AbstractAgentRequestHandler<ResumeSessionRequest, Flowable<BaseEvent>> {
  public ResumeAguiEventsHandler(final AgentExecutionService agentExecutionService) {
    super(agentExecutionService);
  }

  @Override
  public Flowable<BaseEvent> handle(final ResumeSessionRequest resumeRequest) {
    final String agentId = resumeRequest.getAgentId();
    final String sessionId = resumeRequest.getSessionId();
    final AGUIEventMapper mapper = new AGUIEventMapper(sessionId, agentId);
    return mapper.map(agentExecutionService().resumeSession(agentId, sessionId, resumeRequest.getDecision(), resumeRequest.getMessage()));
  }

  @Override
  public AgentRequest.RequestType requestType() {
    return RESUME_SESSION;
  }
}

package com.agentengine.interfaces.rest.handlers;

import static com.agentengine.interfaces.rest.dto.AgentRequest.RequestType.RESUME_SESSION;

import com.agentengine.engine.api.beans.ConfirmationDecision;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.interfaces.rest.requests.ResumeSessionRequest;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;

@Singleton
public final class ResumeAguiEventsHandler
    extends AbstractAgentRequestHandler<ResumeSessionRequest, Flowable<BaseEvent>> {
  public ResumeAguiEventsHandler(final AgentExecutionService agentExecutionService) {
    super(agentExecutionService);
  }

  @Override
  public Flowable<BaseEvent> handle(final ResumeSessionRequest resumeRequest) {
    final ConfirmationDecision decision = resumeRequest.getDecisionEnum();
    final String agentId = resumeRequest.getAgentId();
    final String sessionId = resumeRequest.getSessionId();
    final AGUIEventMapper mapper = new AGUIEventMapper(sessionId, agentId);
    return mapper.map(agentExecutionService.resumeSession(agentId, sessionId, decision, resumeRequest.getMessage()));
  }

  @Override
  public AgentRequest.RequestType requestType() {
    return RESUME_SESSION;
  }
}

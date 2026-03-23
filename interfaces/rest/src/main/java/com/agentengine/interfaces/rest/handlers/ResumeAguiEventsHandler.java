package com.agentengine.interfaces.rest.handlers;

import static com.agentengine.interfaces.rest.dto.AgentRequest.RequestType.RESUME_SESSION;

import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.ResumeSessionRequest;
import com.agentengine.util.ms.MicroServiceClientProvider;
import com.agui.core.event.BaseEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

@Singleton
public final class ResumeAguiEventsHandler extends AbstractAgentRequestHandler<ResumeSessionRequest, Publisher<BaseEvent>> {
  
  @Inject
  public ResumeAguiEventsHandler(final MicroServiceClientProvider microServiceClientProvider) {
    super(microServiceClientProvider);
  }

  @Override
  public Publisher<BaseEvent> handle(final ResumeSessionRequest resumeRequest) {
    return agentExecutionService().resumeSession(resumeRequest.getAgentId(), resumeRequest.getSessionId(),
        resumeRequest.getConfirmationId(), resumeRequest.getConfirmed(), resumeRequest.getMessage());
  }

  @Override
  public AgentRequest.RequestType requestType() {
    return RESUME_SESSION;
  }
}

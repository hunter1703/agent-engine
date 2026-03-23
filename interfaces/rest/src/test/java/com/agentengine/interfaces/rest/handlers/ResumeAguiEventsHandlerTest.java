package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.ResumeSessionRequest;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ResumeAguiEventsHandlerTest {

  @Test
  void shouldDelegateResumeToExecutionService() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.resumeSession(eq("agent-1"), eq("session-1"), eq("pause-1"), eq(Boolean.TRUE), eq("resume")))
        .thenReturn(Flowable.empty());
    final ResumeAguiEventsHandler handler = new ResumeAguiEventsHandler(executionService);
    final ResumeSessionRequest request = new ResumeSessionRequest("resume", true, "pause-1");
    request.setAgentId("agent-1");
    request.setSessionId("session-1");
    request.setConfirmationId("pause-1");

    final Flowable<BaseEvent> publisher = handler.handle(request);

    assertThat(publisher).isNotNull();
    publisher.test().awaitDone(2, TimeUnit.SECONDS).assertNoErrors().assertComplete();
    verify(executionService).resumeSession("agent-1", "session-1", "pause-1", true, "resume");
  }

  @Test
  void shouldUseUnknownDecisionWhenConfirmationIsMissing() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.resumeSession(eq("agent-1"), eq("session-1"), eq("pause-2"), isNull(), eq("resume")))
        .thenReturn(Flowable.empty());
    final ResumeAguiEventsHandler handler = new ResumeAguiEventsHandler(executionService);
    final ResumeSessionRequest request = new ResumeSessionRequest("resume", null, "pause-2");
    request.setAgentId("agent-1");
    request.setSessionId("session-1");
    request.setConfirmationId("pause-2");

    handler.handle(request).test().awaitDone(2, TimeUnit.SECONDS).assertNoErrors().assertComplete();

    verify(executionService).resumeSession("agent-1", "session-1", "pause-2", null, "resume");
  }
}

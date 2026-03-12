package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.ConfirmationDecision;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.requests.ResumeSessionRequest;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.core.event.BaseEvent;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeAguiEventsHandlerTest {

  @Test
  void shouldMapMissingSessionToNotFound() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.resumeSession(eq("session-1"), eq(ConfirmationDecision.UNKNOWN), eq("resume")))
        .thenThrow(new AssetNotFoundException("session", "session-1"));
    final ResumeAguiEventsHandler handler = new ResumeAguiEventsHandler(executionService);
    final ResumeSessionRequest request = new ResumeSessionRequest("resume");
    request.setSessionId("session-1");

    assertThatThrownBy(() -> handler.handle(request))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(404);
  }

  @Test
  void shouldMapValidationFailuresToBadRequest() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.resumeSession(eq("session-1"), eq(ConfirmationDecision.UNKNOWN), eq("resume")))
        .thenThrow(new IllegalArgumentException("decision is required for this confirmation"));
    final ResumeAguiEventsHandler handler = new ResumeAguiEventsHandler(executionService);
    final ResumeSessionRequest request = new ResumeSessionRequest("resume");
    request.setSessionId("session-1");

    assertThatThrownBy(() -> handler.handle(request))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldDelegateResumeToExecutionService() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    final Event event =
        Event.builder()
            .id("event-1")
            .invocationId("inv-1")
            .author("agent-1")
            .content(Content.builder().role("model").parts(List.of(Part.fromText("ok"))).build())
            .build();
    when(executionService.resumeSession(eq("session-1"), eq(ConfirmationDecision.ALLOW), eq(null)))
        .thenReturn(new AgentExecutionService.ResumeExecution("session-1", "agent-1", Flowable.just(event)));
    final ResumeAguiEventsHandler handler = new ResumeAguiEventsHandler(executionService);
    final ResumeSessionRequest request = new ResumeSessionRequest(null, ConfirmationDecision.ALLOW);
    request.setSessionId("session-1");

    final Flowable<BaseEvent> publisher = handler.handle(request);

    assertThat(publisher).isNotNull();
    verify(executionService).resumeSession("session-1", ConfirmationDecision.ALLOW, null);
  }
}

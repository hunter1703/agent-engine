package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import io.reactivex.rxjava3.core.Flowable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StreamAguiEventsRequestHandlerTest {

  @Test
  void shouldAssignSessionIdWhenMissing() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.run(anyString(), anyString(), anyString())).thenReturn(Flowable.empty());
    final StreamAguiEventsRequestHandler handler = new StreamAguiEventsRequestHandler(executionService);

    final AgentRequest request = new AgentRequest();
    request.setType(AgentRequest.RequestType.STREAM_AGUI_EVENTS.name());
    request.setAgentId("echo_agent");
    request.setMessage("hello");

    handler.handle(request).test().awaitDone(2, TimeUnit.SECONDS).assertNoErrors().assertComplete();

    assertThat(request.getSessionId()).isNotBlank();
    verify(executionService).run(same(request.getAgentId()), same(request.getSessionId()), same(request.getMessage()));
  }

  @Test
  void shouldKeepProvidedSessionId() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.run(anyString(), anyString(), anyString())).thenReturn(Flowable.empty());
    final StreamAguiEventsRequestHandler handler = new StreamAguiEventsRequestHandler(executionService);

    final AgentRequest request = new AgentRequest();
    request.setType(AgentRequest.RequestType.STREAM_AGUI_EVENTS.name());
    request.setAgentId("echo_agent");
    request.setSessionId("session-existing-123");
    request.setMessage("hello");

    handler.handle(request).test().awaitDone(2, TimeUnit.SECONDS).assertNoErrors().assertComplete();

    assertThat(request.getSessionId()).isEqualTo("session-existing-123");
    verify(executionService).run(same(request.getAgentId()), same(request.getSessionId()), same(request.getMessage()));
  }
}

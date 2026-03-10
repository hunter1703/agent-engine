package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.services.AgentExecutionService;
import io.reactivex.rxjava3.core.Flowable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StreamAguiEventsRequestHandlerTest {

  @Test
  void shouldAssignSessionIdWhenMissing() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.run(any())).thenReturn(Flowable.empty());
    final StreamAguiEventsRequestHandler handler = new StreamAguiEventsRequestHandler(executionService);

    final AgentRequest request = new AgentRequest();
    request.setType(AgentRequest.RequestType.STREAM_AGUI_EVENTS.name());
    request.setAgentId("echo_agent");

    handler.handle(request).test().awaitDone(2, TimeUnit.SECONDS).assertNoErrors().assertComplete();

    assertThat(request.getSessionId()).isNotBlank();
    verify(executionService).run(same(request));
  }

  @Test
  void shouldKeepProvidedSessionId() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.run(any())).thenReturn(Flowable.empty());
    final StreamAguiEventsRequestHandler handler = new StreamAguiEventsRequestHandler(executionService);

    final AgentRequest request = new AgentRequest();
    request.setType(AgentRequest.RequestType.STREAM_AGUI_EVENTS.name());
    request.setAgentId("echo_agent");
    request.setSessionId("session-existing-123");

    handler.handle(request).test().awaitDone(2, TimeUnit.SECONDS).assertNoErrors().assertComplete();

    assertThat(request.getSessionId()).isEqualTo("session-existing-123");
    verify(executionService).run(same(request));
  }
}

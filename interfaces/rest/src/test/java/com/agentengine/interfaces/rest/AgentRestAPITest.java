package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.requests.ResumeSessionRequest;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

class AgentRestAPITest {

  @Test
  void shouldThrowBadRequestWhenCreateAgentCalledWithNullConfig() {
    final AgentRestAPI api =
        new AgentRestAPI(handlerInstanceWith(new StreamEventsHandler()), mock(AgentService.class), mock(SessionService.class));

    assertThatThrownBy(() -> api.createAgent(null))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowBadRequestWhenUpdateAgentCalledWithBlankAgentId() {
    final AgentRestAPI api =
        new AgentRestAPI(handlerInstanceWith(new StreamEventsHandler()), mock(AgentService.class), mock(SessionService.class));

    assertThatThrownBy(() -> api.updateAgent(" ", new DefaultAgentConfig()))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowNotFoundWhenResumeEventsCalledForUnknownSession() {
    final SessionService sessionService = mock(SessionService.class);
    when(sessionService.getSession("session-1")).thenReturn(java.util.Optional.empty());

    final AgentRestAPI api =
        new AgentRestAPI(handlerInstanceWith(new StreamEventsHandler()), mock(AgentService.class), sessionService);

    assertThatThrownBy(() -> api.resumeEvents("session-1", new ResumeSessionRequest("resume")))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(404);
  }

  @Test
  void shouldUseEventsHandlerWhenResumeEventsCalledForExistingSession() {
    final SessionService sessionService = mock(SessionService.class);
    final AgentSession session = new AgentSession("session-1", "agent-1", "title");
    when(sessionService.getSession("session-1")).thenReturn(java.util.Optional.of(session));

    final StreamEventsHandler handler = new StreamEventsHandler();
    final AgentRestAPI api =
        new AgentRestAPI(handlerInstanceWith(handler), mock(AgentService.class), sessionService);

    final Publisher<BaseEvent> publisher =
        api.resumeEvents("session-1", new ResumeSessionRequest("resume message"));

    assertThat(publisher).isNotNull();
    assertThat(handler.lastRequest).isNotNull();
    assertThat(handler.lastRequest.getSessionId()).isEqualTo("session-1");
    assertThat(handler.lastRequest.getAgentId()).isEqualTo("agent-1");
    assertThat(handler.lastRequest.getMessage()).isEqualTo("resume message");
  }

  @Test
  void shouldThrowBadRequestWhenEventsCalledWithoutRegisteredHandler() {
    final Instance<AgentRequestHandler<?>> emptyHandlers = handlerInstanceWith();
    final AgentRestAPI api = new AgentRestAPI(emptyHandlers, mock(AgentService.class), mock(SessionService.class));

    assertThatThrownBy(() -> api.events(new AgentRequest()))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldDelegateDeleteAgentWhenDeleteAgentCalled() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.deleteAgent("agent-1")).thenReturn(true);
    final AgentRestAPI api =
        new AgentRestAPI(handlerInstanceWith(new StreamEventsHandler()), agentService, mock(SessionService.class));

    final boolean deleted = api.deleteAgent("agent-1");

    assertThat(deleted).isTrue();
    verify(agentService).deleteAgent("agent-1");
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  private static Instance<AgentRequestHandler<?>> handlerInstanceWith(
      final AgentRequestHandler<?>... handlers) {
    final Instance<AgentRequestHandler<?>> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(java.util.stream.Stream.of(handlers));
    return instance;
  }

  private static final class StreamEventsHandler implements AgentRequestHandler<Flowable<BaseEvent>> {
    private AgentRequest lastRequest;

    @Override
    public AgentRequest.RequestType requestType() {
      return AgentRequest.RequestType.STREAM_AGUI_EVENTS;
    }

    @Override
    public Flowable<BaseEvent> handle(final AgentRequest request) {
      this.lastRequest = request;
      return Flowable.empty();
    }
  }
}

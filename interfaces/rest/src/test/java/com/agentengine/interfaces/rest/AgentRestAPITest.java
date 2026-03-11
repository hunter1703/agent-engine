package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.beans.session.SessionInfo;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.requests.ResumeSessionRequest;
import com.agentengine.interfaces.rest.requests.ResumeSessionRequest.ConfirmationDecision;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

class AgentRestAPITest {

  @Test
  void shouldThrowBadRequestWhenCreateAgentCalledWithNullConfig() {
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            mock(AgentService.class),
            mock(SessionService.class));

    assertThatThrownBy(() -> api.createAgent(null))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldDelegateCreateAgentValidationToService() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.createAgent(any(BaseAgentConfig.class)))
        .thenThrow(new IllegalArgumentException("Agent type and modelId are required"));
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));
    final DefaultAgentConfig payload = new DefaultAgentConfig();
    payload.setId("agent-1");

    assertThatThrownBy(() -> api.createAgent(payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agent type and modelId are required");
    verify(agentService).createAgent(any(BaseAgentConfig.class));
  }

  @Test
  void shouldThrowBadRequestWhenUpdateAgentCalledWithBlankAgentId() {
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            mock(AgentService.class),
            mock(SessionService.class));

    assertThatThrownBy(() -> api.updateAgent(" ", new DefaultAgentConfig()))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowBadRequestWhenUpdateAgentIdMismatch() {
    final AgentService agentService = mock(AgentService.class);
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));
    final DefaultAgentConfig payload = new DefaultAgentConfig();
    payload.setId("agent-2");
    payload.setType("default");
    payload.setModelId("model-1");

    assertThatThrownBy(() -> api.updateAgent("agent-1", payload))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowNotFoundWhenResumeEventsCalledForUnknownSession() {
    final SessionService sessionService = mock(SessionService.class);
    when(sessionService.getSession("session-1")).thenReturn(java.util.Optional.empty());

    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            mock(AgentService.class),
            sessionService);

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

    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent("agent-1"))
        .thenReturn(java.util.Optional.of(new DefaultAgentConfig()));

    final StreamEventsHandler handler = new StreamEventsHandler();
    final AgentRestAPI api =
        new AgentRestAPI(handlerInstanceWith(handler), agentService, sessionService);

    final Publisher<BaseEvent> publisher =
        api.resumeEvents("session-1", new ResumeSessionRequest("resume message"));

    assertThat(publisher).isNotNull();
    assertThat(handler.lastRequest).isNotNull();
    assertThat(handler.lastRequest.getSessionId()).isEqualTo("session-1");
    assertThat(handler.lastRequest.getAgentId()).isEqualTo("agent-1");
    assertThat(handler.lastRequest.getMessage()).isEqualTo("resume message");
  }

  @Test
  void shouldRejectResumeWhenSessionIsExpired() {
    final SessionService sessionService = mock(SessionService.class);
    final AgentSession session = new AgentSession("session-1", "agent-1", "title");
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setState(Map.of("expiresAt", Instant.parse("2000-01-01T00:00:00Z").toString()));
    session.setSessionInfo(sessionInfo);
    when(sessionService.getSession("session-1")).thenReturn(java.util.Optional.of(session));

    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            mock(AgentService.class),
            sessionService);

    assertThatThrownBy(() -> api.resumeEvents("session-1", new ResumeSessionRequest("resume")))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(410);
  }

  @Test
  void shouldRejectResumeWhenToolConfirmationIsTimedOut() {
    final SessionService sessionService = mock(SessionService.class);
    final AgentSession session = new AgentSession("session-1", "agent-1", "title");
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setState(
        Map.of(
            "sessionState",
            Map.of(
                "paused",
                true,
                "pauseReason",
                "tool_confirmation",
                "pauseRequestedAt",
                Instant.now().minusSeconds(3_600).toEpochMilli())));
    session.setSessionInfo(sessionInfo);
    when(sessionService.getSession("session-1")).thenReturn(java.util.Optional.of(session));

    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            mock(AgentService.class),
            sessionService);

    assertThatThrownBy(() -> api.resumeEvents("session-1", new ResumeSessionRequest("resume")))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(408);
  }

  @Test
  void shouldRequireDecisionWhenResumingToolConfirmationPause() {
    final SessionService sessionService = mock(SessionService.class);
    final AgentSession session = new AgentSession("session-1", "agent-1", "title");
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setState(
        Map.of(
            "sessionState",
            Map.of(
                "paused",
                true,
                "pauseReason",
                "tool_confirmation",
                "pauseRequestedAt",
                Instant.now().toEpochMilli())));
    session.setSessionInfo(sessionInfo);
    when(sessionService.getSession("session-1")).thenReturn(java.util.Optional.of(session));

    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            mock(AgentService.class),
            sessionService);

    assertThatThrownBy(() -> api.resumeEvents("session-1", new ResumeSessionRequest("yes")))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldEncodeDecisionMarkerWhenResumingToolConfirmationPause() {
    final SessionService sessionService = mock(SessionService.class);
    final AgentSession session = new AgentSession("session-1", "agent-1", "title");
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setState(
        Map.of(
            "sessionState",
            Map.of(
                "paused",
                true,
                "pauseReason",
                "tool_confirmation",
                "pauseRequestedAt",
                Instant.now().toEpochMilli())));
    session.setSessionInfo(sessionInfo);
    when(sessionService.getSession("session-1")).thenReturn(java.util.Optional.of(session));

    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent("agent-1"))
        .thenReturn(java.util.Optional.of(new DefaultAgentConfig()));
    final StreamEventsHandler handler = new StreamEventsHandler();
    final AgentRestAPI api =
        new AgentRestAPI(handlerInstanceWith(handler), agentService, sessionService);

    api.resumeEvents("session-1", new ResumeSessionRequest("", ConfirmationDecision.APPROVE));

    assertThat(handler.lastRequest.getMessage()).isEqualTo("__AE_TOOL_DECISION__:APPROVE");
  }

  @Test
  void shouldThrowBadRequestWhenEventsCalledWithoutRegisteredHandler() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent(any())).thenReturn(java.util.Optional.of(new DefaultAgentConfig()));
    final Instance<AgentRequestHandler<?>> emptyHandlers = handlerInstanceWith();
    final AgentRestAPI api =
        new AgentRestAPI(emptyHandlers, agentService, mock(SessionService.class));

    assertThatThrownBy(() -> api.events(new AgentRequest()))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowNotFoundWhenEventsCalledWithUnknownAgentId() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent("ghost")).thenReturn(java.util.Optional.empty());
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));
    final AgentRequest req = new AgentRequest();
    req.setType("STREAM_AGUI_EVENTS");
    req.setAgentId("ghost");
    req.setMessage("hi");

    assertThatThrownBy(() -> api.events(req))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(404);
  }

  @Test
  void shouldDelegateUpsertSubAgentValidationToService() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.saveAgent(any(BaseAgentConfig.class)))
        .thenThrow(new IllegalArgumentException("Sub-agent(s) not found: missing_subagent"));
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));
    final OrchestratorAgentConfig payload = new OrchestratorAgentConfig();
    payload.setId("orch-1");
    payload.setType("orchestrator");
    payload.setModelId("model-1");
    payload.setSubAgentIds(List.of("story_phase_1_brief", "missing_subagent"));

    assertThatThrownBy(() -> api.upsertAgent(payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sub-agent(s) not found");
    verify(agentService).saveAgent(any(BaseAgentConfig.class));
  }

  @Test
  void shouldAllowUpsertOrchestratorWithoutModelId() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.saveAgent(any(BaseAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));
    final OrchestratorAgentConfig payload = new OrchestratorAgentConfig();
    payload.setId("orch-2");
    payload.setType("orchestrator");
    payload.setSubAgentIds(List.of());

    final BaseAgentConfig saved = api.upsertAgent(payload);

    assertThat(saved.getId()).isEqualTo("orch-2");
  }

  @Test
  void shouldDelegateDeleteAgentWhenDeleteAgentCalled() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.deleteAgent("agent-1")).thenReturn(true);
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));

    final boolean deleted = api.deleteAgent("agent-1");

    assertThat(deleted).isTrue();
    verify(agentService).deleteAgent("agent-1");
  }

  @Test
  void shouldThrowNotFoundWhenDeleteAgentCalledForMissingId() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.deleteAgent("agent-1")).thenReturn(false);
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));

    assertThatThrownBy(() -> api.deleteAgent("agent-1"))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(404);
  }

  @Test
  void shouldDelegateUpdateAgentWhenUpdateAgentCalled() {
    final AgentService agentService = mock(AgentService.class);
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));
    final DefaultAgentConfig payload = new DefaultAgentConfig();
    payload.setType("default");
    payload.setModelId("model-1");
    when(agentService.updateAgent(eq("agent-1"), any(BaseAgentConfig.class)))
        .thenAnswer(inv -> inv.getArgument(1));

    final BaseAgentConfig updated = api.updateAgent("agent-1", payload);

    assertThat(updated).isNotNull();
    assertThat(updated.getId()).isNull();
    verify(agentService).updateAgent(eq("agent-1"), any(BaseAgentConfig.class));
  }

  @Test
  void shouldDelegateCreateSubAgentValidationToService() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.createAgent(any(BaseAgentConfig.class)))
        .thenThrow(new IllegalArgumentException("Sub-agent(s) not found: missing-sub"));
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("orch-1");
    config.setType("orchestrator");
    config.setSubAgentIds(java.util.List.of("missing-sub"));
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));

    assertThatThrownBy(() -> api.createAgent(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sub-agent(s) not found");
    verify(agentService).createAgent(any(BaseAgentConfig.class));
  }

  @Test
  void shouldNotCallGetAgentBeforeCreateAgent() {
    final AgentService agentService = mock(AgentService.class);
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("a-1");
    config.setType("default");
    config.setModelId("model-1");
    when(agentService.createAgent(any())).thenReturn(config);
    new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class))
        .createAgent(config);
    verify(agentService, never()).getAgent(any());
  }

  @Test
  void shouldThrowBadRequestWhenDeleteAgentCalledWithBlankId() {
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            mock(AgentService.class),
            mock(SessionService.class));

    assertThatThrownBy(() -> api.deleteAgent(" "))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  private static Instance<AgentRequestHandler<?>> handlerInstanceWith(
      final AgentRequestHandler<?>... handlers) {
    final Instance<AgentRequestHandler<?>> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(java.util.stream.Stream.of(handlers));
    return instance;
  }

  private static final class StreamEventsHandler
      implements AgentRequestHandler<Flowable<BaseEvent>> {
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

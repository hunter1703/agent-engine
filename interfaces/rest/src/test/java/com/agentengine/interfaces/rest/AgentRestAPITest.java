package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.interfaces.rest.requests.ResumeSessionRequest;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Optional;
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
  void shouldDelegateUpdateAgentIdMismatchValidationToService() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.updateAgent(eq("agent-1"), any(BaseAgentConfig.class)))
        .thenThrow(new IllegalArgumentException("Agent ID in path and payload must match"));
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            agentService,
            mock(SessionService.class));
    final DefaultAgentConfig payload = new DefaultAgentConfig();
    payload.setId("agent-2");
    payload.setType("DEFAULT");
    payload.setModelId("model-1");

    assertThatThrownBy(() -> api.updateAgent("agent-1", payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match");
    verify(agentService).updateAgent(eq("agent-1"), any(BaseAgentConfig.class));
  }

  @Test
  void shouldUseResumeHandlerWhenResumeEventsCalled() {
    final ResumeEventsHandler handler = new ResumeEventsHandler();
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler(), handler),
            mock(AgentService.class),
            mock(SessionService.class));
    final ResumeSessionRequest request = new ResumeSessionRequest("Paris");
    request.setSessionId("session-request-1");

    final Publisher<BaseEvent> publisher = api.resumeEvents("session-path-ignored", request);

    assertThat(publisher).isNotNull();
    assertThat(handler.lastRequest).isSameAs(request);
    assertThat(handler.lastRequest.getSessionId()).isEqualTo("session-request-1");
  }

  @Test
  void shouldUseEventsHandlerWhenEventsCalledForExistingAgent() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent("agent-1"))
        .thenReturn(Optional.of(new DefaultAgentConfig()));

    final StreamEventsHandler handler = new StreamEventsHandler();
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(handler),
            agentService,
            mock(SessionService.class));
    final AgentRequest request = new AgentRequest();
    request.setType("STREAM_AGUI_EVENTS");
    request.setAgentId("agent-1");
    request.setMessage("hello");

    final Publisher<BaseEvent> publisher = api.events(request);

    assertThat(publisher).isNotNull();
    assertThat(handler.lastRequest).isSameAs(request);
  }

  @Test
  void shouldThrowBadRequestWhenResumeEventsCalledWithoutRegisteredHandler() {
    final AgentRestAPI api =
        new AgentRestAPI(
            handlerInstanceWith(new StreamEventsHandler()),
            mock(AgentService.class),
            mock(SessionService.class));
    final ResumeSessionRequest request = new ResumeSessionRequest("Paris");
    request.setSessionId("session-1");

    assertThatThrownBy(() -> api.resumeEvents("session-path-ignored", request))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowBadRequestWhenEventsCalledWithoutRegisteredHandler() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent(any())).thenReturn(Optional.of(new DefaultAgentConfig()));
    final Instance<AgentRequestHandler<?, ?>> emptyHandlers = handlerInstanceWith();
    final AgentRestAPI api =
        new AgentRestAPI(
            emptyHandlers,
            agentService,
            mock(SessionService.class));

    assertThatThrownBy(() -> api.events(new AgentRequest()))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowNotFoundWhenEventsCalledWithUnknownAgentId() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent("ghost")).thenReturn(Optional.empty());
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
    payload.setType("ORCHESTRATOR");
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
    payload.setType("ORCHESTRATOR");
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
    payload.setType("DEFAULT");
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
    config.setType("ORCHESTRATOR");
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
    config.setType("DEFAULT");
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
  private static Instance<AgentRequestHandler<?, ?>> handlerInstanceWith(
      final AgentRequestHandler<?, ?>... handlers) {
    final Instance<AgentRequestHandler<?, ?>> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(java.util.stream.Stream.of(handlers));
    when(instance.isUnsatisfied()).thenReturn(handlers.length == 0);
    return instance;
  }

  private static final class StreamEventsHandler
      implements AgentRequestHandler<AgentRequest, Flowable<BaseEvent>> {
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

  private static final class ResumeEventsHandler
      implements AgentRequestHandler<ResumeSessionRequest, Flowable<BaseEvent>> {
    private ResumeSessionRequest lastRequest;

    @Override
    public AgentRequest.RequestType requestType() {
      return AgentRequest.RequestType.RESUME_SESSION;
    }

    @Override
    public Flowable<BaseEvent> handle(final ResumeSessionRequest request) {
      this.lastRequest = request;
      return Flowable.empty();
    }
  }
}

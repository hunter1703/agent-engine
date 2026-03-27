package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.core.api.services.AgentService;
import com.agentengine.core.api.services.SessionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.ResumeSessionRequest;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.DefaultAgentConfig;
import com.agentengine.util.agents.beans.config.OrchestratorAgentConfig;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

public class AgentRestAPITest {

  @Test
  public void shouldThrowBadRequestWhenCreateAgentCalledWithNullConfig() {
    final AgentRestAPI api = new AgentRestAPI(mock(AgentService.class), mock(SessionService.class), mock(AgentExecutionService.class));

    assertThatThrownBy(() -> api.createAgent(null)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agent config is required");
  }

  @Test
  public void shouldDelegateCreateAgentValidationToService() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.createAgent(any(BaseAgentConfig.class)))
        .thenThrow(new IllegalArgumentException("Agent type and modelId are required"));
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));
    final DefaultAgentConfig payload = new DefaultAgentConfig();
    payload.setId("agent-1");

    assertThatThrownBy(() -> api.createAgent(payload)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agent type and modelId are required");
    verify(agentService).createAgent(any(BaseAgentConfig.class));
  }

  @Test
  public void shouldThrowBadRequestWhenUpdateAgentCalledWithBlankAgentId() {
    final AgentRestAPI api = new AgentRestAPI(mock(AgentService.class), mock(SessionService.class), mock(AgentExecutionService.class));

    assertThatThrownBy(() -> api.updateAgent(" ", new DefaultAgentConfig())).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agent ID is required");
  }

  @Test
  public void shouldDelegateUpdateAgentIdMismatchValidationToService() {
    final AgentService agentService = mock(AgentService.class);
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));
    final DefaultAgentConfig payload = new DefaultAgentConfig();
    payload.setId("agent-2");
    payload.setType("DEFAULT");
    payload.setModelId("model-1");

    assertThatThrownBy(() -> api.updateAgent("agent-1", payload)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match");
    verify(agentService, never()).updateAgent(anyString(), any(BaseAgentConfig.class));
  }

  @Test
  public void shouldUseExecutionServiceWhenResumeEventsCalled() {
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.resumeSession("session-request-1", "confirmation-1", true, "Paris")).thenReturn(Flowable.empty());
    final AgentRestAPI api = new AgentRestAPI(mock(AgentService.class), mock(SessionService.class), executionService);
    final ResumeSessionRequest request = new ResumeSessionRequest("Paris");
    request.setSessionId("session-request-1");
    request.setConfirmationId("confirmation-1");
    request.setConfirmed(true);

    final Publisher<BaseEvent> publisher = api.resumeEvents(request);

    assertThat(publisher).isNotNull();
    verify(executionService).resumeSession("session-request-1", "confirmation-1", true, "Paris");
  }

  @Test
  public void shouldUseExecutionServiceWhenEventsCalledForExistingAgent() {
    final AgentService agentService = mock(AgentService.class);
    final AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(agentService.getAgent("agent-1")).thenReturn(new DefaultAgentConfig());
    when(executionService.run("agent-1", "hello")).thenReturn(Flowable.empty());

    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), executionService);
    final AgentRequest request = new AgentRequest();
    request.setType("STREAM_AGUI_EVENTS");
    request.setAgentId("agent-1");
    request.setMessage("hello");

    final Publisher<BaseEvent> publisher = api.events(request);

    assertThat(publisher).isNotNull();
    verify(executionService).run("agent-1", "hello");
  }

  @Test
  public void shouldThrowNotFoundWhenEventsCalledWithUnknownAgentId() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.getAgent("ghost")).thenReturn(null);
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));
    final AgentRequest req = new AgentRequest();
    req.setType("STREAM_AGUI_EVENTS");
    req.setAgentId("ghost");
    req.setMessage("hi");

    assertThatThrownBy(() -> api.events(req)).hasMessageContaining("ghost");
  }

  @Test
  public void shouldDelegateUpsertSubAgentValidationToService() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.saveAgent(any(BaseAgentConfig.class)))
        .thenThrow(new IllegalArgumentException("Sub-agent(s) not found: missing_subagent"));
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));
    final OrchestratorAgentConfig payload = new OrchestratorAgentConfig();
    payload.setId("orch-1");
    payload.setType("ORCHESTRATOR");
    payload.setModelId("model-1");
    payload.setSubAgentIds(List.of("story_phase_1_brief", "missing_subagent"));

    assertThatThrownBy(() -> api.upsertAgent(payload)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sub-agent(s) not found");
    verify(agentService).saveAgent(any(BaseAgentConfig.class));
  }

  @Test
  public void shouldAllowUpsertOrchestratorWithoutModelId() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.saveAgent(any(BaseAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));
    final OrchestratorAgentConfig payload = new OrchestratorAgentConfig();
    payload.setId("orch-2");
    payload.setType("ORCHESTRATOR");
    payload.setSubAgentIds(List.of());

    final BaseAgentConfig saved = api.upsertAgent(payload);

    assertThat(saved.getId()).isEqualTo("orch-2");
  }

  @Test
  public void shouldDelegateDeleteAgentWhenDeleteAgentCalled() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.deleteAgent("agent-1")).thenReturn(true);
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));

    final boolean deleted = api.deleteAgent("agent-1");

    assertThat(deleted).isTrue();
    verify(agentService).deleteAgent("agent-1");
  }

  @Test
  public void shouldThrowNotFoundWhenDeleteAgentCalledForMissingId() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.deleteAgent("agent-1")).thenReturn(false);
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));

    assertThatThrownBy(() -> api.deleteAgent("agent-1")).hasMessageContaining("agent-1");
  }

  @Test
  public void shouldDelegateUpdateAgentWhenUpdateAgentCalled() {
    final AgentService agentService = mock(AgentService.class);
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));
    final DefaultAgentConfig payload = new DefaultAgentConfig();
    payload.setType("DEFAULT");
    payload.setModelId("model-1");
    when(agentService.updateAgent(eq("agent-1"), any(BaseAgentConfig.class))).thenAnswer(inv -> inv.getArgument(1));

    final BaseAgentConfig updated = api.updateAgent("agent-1", payload);

    assertThat(updated).isNotNull();
    assertThat(updated.getId()).isNull();
    verify(agentService).updateAgent(eq("agent-1"), any(BaseAgentConfig.class));
  }

  @Test
  public void shouldDelegateCreateSubAgentValidationToService() {
    final AgentService agentService = mock(AgentService.class);
    when(agentService.createAgent(any(BaseAgentConfig.class)))
        .thenThrow(new IllegalArgumentException("Sub-agent(s) not found: missing-sub"));
    final OrchestratorAgentConfig config = new OrchestratorAgentConfig();
    config.setId("orch-1");
    config.setType("ORCHESTRATOR");
    config.setSubAgentIds(List.of("missing-sub"));
    final AgentRestAPI api = new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class));

    assertThatThrownBy(() -> api.createAgent(config)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sub-agent(s) not found");
    verify(agentService).createAgent(any(BaseAgentConfig.class));
  }

  @Test
  public void shouldNotCallGetAgentBeforeCreateAgent() {
    final AgentService agentService = mock(AgentService.class);
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("a-1");
    config.setType("DEFAULT");
    config.setModelId("model-1");
    when(agentService.createAgent(any())).thenReturn(config);

    new AgentRestAPI(agentService, mock(SessionService.class), mock(AgentExecutionService.class)).createAgent(config);

    verify(agentService, never()).getAgent(any());
  }

  @Test
  public void shouldThrowBadRequestWhenDeleteAgentCalledWithBlankId() {
    final AgentRestAPI api = new AgentRestAPI(mock(AgentService.class), mock(SessionService.class), mock(AgentExecutionService.class));

    assertThatThrownBy(() -> api.deleteAgent(" ")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agent ID is required");
  }

  @Test
  public void shouldDelegateDeleteSessionWhenDeleteSessionCalled() {
    final SessionService sessionService = mock(SessionService.class);
    when(sessionService.deleteSession("session-1")).thenReturn(true);
    final AgentRestAPI api = new AgentRestAPI(mock(AgentService.class), sessionService, mock(AgentExecutionService.class));

    api.deleteSession("session-1");

    verify(sessionService).deleteSession("session-1");
  }

  @Test
  public void shouldStillDelegateDeleteSessionWhenSessionMissing() {
    final SessionService sessionService = mock(SessionService.class);
    when(sessionService.deleteSession("session-1")).thenReturn(false);
    final AgentRestAPI api = new AgentRestAPI(mock(AgentService.class), sessionService, mock(AgentExecutionService.class));

    api.deleteSession("session-1");

    verify(sessionService).deleteSession("session-1");
  }
}

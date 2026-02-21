package com.agentengine.engine.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.google.adk.agents.LlmAgent;
import com.google.adk.sessions.BaseSessionService;
import io.reactivex.rxjava3.core.Single;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentSessionRuntimeManagerTest {

  @Test
  void throwsExceptionWhenAgentConfigIsMissing() {
    final AgentRepository agentRepository = mock(AgentRepository.class);
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final AgentSessionRepository agentSessionRepository = mock(AgentSessionRepository.class);

    when(agentRepository.findById(anyString())).thenReturn(Optional.empty());

    final AgentSessionRuntimeManager manager =
        new AgentSessionRuntimeManager(
            agentRepository, agentProvider, sessionServiceProvider, agentSessionRepository);

    assertThatThrownBy(() -> manager.getOrStartRuntime("non-existent", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("has no resolved config");
  }

  @Test
  void usesSessionServiceFromProviderWithoutDecoration() {
    final AgentRepository agentRepository = mock(AgentRepository.class);
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final AgentSessionRepository agentSessionRepository = mock(AgentSessionRepository.class);

    final AgentModelConfig modelConfig = new AgentModelConfig();
    modelConfig.setModelId("model-id");
    modelConfig.setSystemPrompt("prompt");

    final AgentConfig agentConfig = new AgentConfig();
    agentConfig.setId("agent-id");
    agentConfig.setModel(modelConfig);

    when(agentRepository.findById("agent-id")).thenReturn(Optional.of(agentConfig));
    when(agentSessionRepository.findById("session-id"))
        .thenReturn(Optional.of(new AgentSession("session-id", "agent-id", "title")));

    final BaseSessionService sessionService = mock(BaseSessionService.class);
    when(sessionServiceProvider.get(any())).thenReturn(sessionService);

    final LlmAgent agent = mock(LlmAgent.class);
    when(agentProvider.get(any(), any())).thenReturn(agent);

    final AgentSessionRuntimeManager manager =
        new AgentSessionRuntimeManager(
            agentRepository, agentProvider, sessionServiceProvider, agentSessionRepository);

    final AgentSessionRuntime runtime = manager.getOrStartRuntime("agent-id", "session-id");

    assertThat(runtime.runner().sessionService()).isSameAs(sessionService);
  }

  @Test
  void cachesRuntimeBySessionId() {
    final AgentRepository agentRepository = mock(AgentRepository.class);
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final AgentSessionRepository agentSessionRepository = mock(AgentSessionRepository.class);

    final AgentModelConfig modelConfig = new AgentModelConfig();
    modelConfig.setModelId("model-id");
    modelConfig.setSystemPrompt("prompt");

    final AgentConfig agentConfig = new AgentConfig();
    agentConfig.setId("agent-id");
    agentConfig.setModel(modelConfig);

    when(agentRepository.findById("agent-id")).thenReturn(Optional.of(agentConfig));
    final BaseSessionService sessionService = mock(BaseSessionService.class);
    when(sessionService.createSession(any(), any(), any(), any()))
        .thenReturn(Single.just(mock(com.google.adk.sessions.Session.class)));
    when(sessionServiceProvider.get(any())).thenReturn(sessionService);
    when(agentProvider.get(any(), any())).thenReturn(mock(LlmAgent.class));

    final AgentSessionRuntimeManager manager =
        new AgentSessionRuntimeManager(
            agentRepository, agentProvider, sessionServiceProvider, agentSessionRepository);

    final AgentSessionRuntime runtime1 = manager.getOrStartRuntime("agent-id", "session-1");
    final AgentSessionRuntime runtime2 = manager.getOrStartRuntime("agent-id", "session-1");

    assertThat(runtime1).isSameAs(runtime2);
  }
}

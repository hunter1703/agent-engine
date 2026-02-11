package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.agents.AgentSessionRuntime;
import com.google.adk.agents.LlmAgent;
import com.google.adk.sessions.InMemorySessionService;
import org.junit.jupiter.api.Test;

class AgentSessionRuntimeManagerTest {

  @Test
  void resolveEngineUsesProvidedNameAndConfigPath() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final AgentRepository configRepository = mock(AgentRepository.class);
    final LlmAgent engine = mock(LlmAgent.class);
    final AgentConfig agentConfig = buildValidAgentConfig();
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);

    when(agentProvider.get(eq(agentConfig), any(AgentContext.class))).thenReturn(engine);
    when(sessionServiceProvider.get(agentConfig.getSessionStore())).thenReturn(new InMemorySessionService());

    final AgentSessionRuntimeManager service = new AgentSessionRuntimeManager(configRepository, agentProvider,
        sessionServiceProvider, null);

    final AgentSessionRuntime resolved = service.getOrStartRuntime("agent", "config.json");

    assertThat(resolved.sessionId()).isNotBlank(); // Verify that a session ID is returned
    verify(agentProvider).get(any(AgentConfig.class), any(AgentContext.class));
  }

  @Test
  void resolveEngineUsesRepositoryConfig() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final AgentRepository configRepository = mock(AgentRepository.class);
    final LlmAgent engine = mock(LlmAgent.class);
    final AgentConfig config = buildValidAgentConfig();
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);

    when(configRepository.findById("agent")).thenReturn(Optional.of(config));
    when(agentProvider.get(eq(config), any(AgentContext.class))).thenReturn(engine);
    when(sessionServiceProvider.get(config.getSessionStore())).thenReturn(new InMemorySessionService());

    final AgentSessionRuntimeManager service = new AgentSessionRuntimeManager(configRepository, agentProvider,
        sessionServiceProvider, null);

    final AgentSessionRuntime resolved = service.getOrStartRuntime("agent", null);

    assertThat(resolved.sessionId()).isNotBlank(); // Verify that a session ID is returned
    // Note: ConfigLoader is no longer used in the constructor, so we can't verify
    // it's never called
  }

  @Test
  void resolveEngineCachesByNameAndConfigPath() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final AgentRepository configRepository = mock(AgentRepository.class);
    final LlmAgent engine = mock(LlmAgent.class);
    final AgentConfig agentConfig = buildValidAgentConfig();
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);

    when(agentProvider.get(eq(agentConfig), any(AgentContext.class))).thenReturn(engine);
    when(sessionServiceProvider.get(agentConfig.getSessionStore())).thenReturn(new InMemorySessionService());

    final AgentSessionRuntimeManager service = new AgentSessionRuntimeManager(configRepository, agentProvider,
        sessionServiceProvider, null);

    final AgentSessionRuntime first = service.getOrStartRuntime("agent", "config.json");
    final AgentSessionRuntime second = service.getOrStartRuntime("agent", "config.json");

    assertThat(first.sessionId()).isEqualTo(second.sessionId());
    verify(agentProvider).get(any(AgentConfig.class), any(AgentContext.class));
  }

  @Test
  void resolveEngineRejectsMissingAgentId() {
    final AgentSessionRuntimeManager service = new AgentSessionRuntimeManager(mock(AgentRepository.class),
        mock(AgentProvider.class), mock(SessionServiceProvider.class), null);

    assertThatThrownBy(() -> service.getOrStartRuntime(" ", "config.json")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("agentId");
  }

  @Test
  void resolveEngineRejectsMissingConfig() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final AgentRepository configRepository = mock(AgentRepository.class);

    when(configRepository.findById("agent")).thenReturn(Optional.empty());

    final AgentSessionRuntimeManager service = new AgentSessionRuntimeManager(configRepository, agentProvider,
        mock(SessionServiceProvider.class), null);

    assertThatThrownBy(() -> service.getOrStartRuntime("agent", null)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("agentId");
  }

  private static AgentConfig buildValidAgentConfig() {
    final AgentConfig config = new AgentConfig();
    config.setId("agent");
    config.setModel(model("reasoner.json"));
    return config;
  }

  private static AgentModelConfig model(final String id) {
    final AgentModelConfig config = new AgentModelConfig();
    config.setModelId(id);
    config.setSystemPrompt("system");
    return config;
  }
}

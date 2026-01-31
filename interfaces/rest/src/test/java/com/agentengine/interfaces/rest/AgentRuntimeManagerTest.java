package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;

import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ConfigLoader;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.google.adk.agents.LlmAgent;
import com.google.adk.sessions.InMemorySessionService;
import org.junit.jupiter.api.Test;

class AgentRuntimeManagerTest {

  @Test
  void resolveEngineUsesProvidedNameAndConfigPath() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final ConfigLoader configLoader = mock(ConfigLoader.class);
    final ConfigRepository configRepository = mock(ConfigRepository.class);
    final LlmAgent engine = mock(LlmAgent.class);
    final AgentConfig agentConfig = buildValidAgentConfig();
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);

    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(agentConfig);
    when(agentProvider.get(agentConfig)).thenReturn(engine);
    when(sessionServiceProvider.get(agentConfig.getSessionStore())).thenReturn(new InMemorySessionService());

    final AgentRuntimeManager service = new AgentRuntimeManager(agentProvider, configLoader, configRepository,
        sessionServiceProvider);

    final AgentRuntime resolved = service.getOrStartRuntime("agent", "config.json");

    assertThat(resolved.agent()).isSameAs(engine);
    verify(agentProvider).get(agentConfig);
  }

  @Test
  void resolveEngineUsesRepositoryConfig() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final ConfigLoader configLoader = mock(ConfigLoader.class);
    final ConfigRepository configRepository = mock(ConfigRepository.class);
    final LlmAgent engine = mock(LlmAgent.class);
    final AgentConfig config = buildValidAgentConfig();
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);

    when(configRepository.loadAgentConfig("agent")).thenReturn(config);
    when(agentProvider.get(config)).thenReturn(engine);
    when(sessionServiceProvider.get(config.getSessionStore())).thenReturn(new InMemorySessionService());

    final AgentRuntimeManager service = new AgentRuntimeManager(agentProvider, configLoader, configRepository,
        sessionServiceProvider);

    final AgentRuntime resolved = service.getOrStartRuntime("agent", null);

    assertThat(resolved.agent()).isSameAs(engine);
    verify(configLoader, never()).loadConfig(any());
  }

  @Test
  void resolveEngineCachesByNameAndConfigPath() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final ConfigLoader configLoader = mock(ConfigLoader.class);
    final ConfigRepository configRepository = mock(ConfigRepository.class);
    final LlmAgent engine = mock(LlmAgent.class);
    final AgentConfig agentConfig = buildValidAgentConfig();
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);

    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(agentConfig);
    when(agentProvider.get(agentConfig)).thenReturn(engine);
    when(sessionServiceProvider.get(agentConfig.getSessionStore())).thenReturn(new InMemorySessionService());

    final AgentRuntimeManager service = new AgentRuntimeManager(agentProvider, configLoader, configRepository,
        sessionServiceProvider);

    final AgentRuntime first = service.getOrStartRuntime("agent", "config.json");
    final AgentRuntime second = service.getOrStartRuntime("agent", "config.json");

    assertThat(first).isSameAs(second);
    verify(agentProvider).get(agentConfig);
  }

  @Test
  void resolveEngineRejectsMissingAgentId() {
    final AgentRuntimeManager service = new AgentRuntimeManager(mock(AgentProvider.class), mock(ConfigLoader.class),
        mock(ConfigRepository.class), mock(SessionServiceProvider.class));

    assertThatThrownBy(() -> service.getOrStartRuntime(" ", "config.json")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("agentId");
  }

  @Test
  void resolveEngineRejectsMissingConfig() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final ConfigLoader configLoader = mock(ConfigLoader.class);
    final ConfigRepository configRepository = mock(ConfigRepository.class);

    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(null);

    final AgentRuntimeManager service = new AgentRuntimeManager(agentProvider, configLoader, configRepository,
        mock(SessionServiceProvider.class));

    assertThatThrownBy(() -> service.getOrStartRuntime("agent", "config.json"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("agentId");
  }

  private static AgentConfig buildValidAgentConfig() {
    final AgentConfig config = new AgentConfig();
    config.setAgentId("agent");
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

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
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.interfaces.rest.services.AgentManager;
import org.junit.jupiter.api.Test;

class AgentManagerTest {

  @Test
  void resolveEngineUsesProvidedNameAndConfigPath() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final ConfigLoader configLoader = mock(ConfigLoader.class);
    final ConfigRepository configRepository = mock(ConfigRepository.class);
    final Agent engine = mock(Agent.class);
    final AgentConfig agentConfig = buildValidAgentConfig();

    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(agentConfig);
    when(agentProvider.get(agentConfig)).thenReturn(engine);

    final AgentManager service = new AgentManager(agentProvider, configLoader, configRepository);

    final Agent resolved = service.getOrStartEngine("agent", "config.json");

    assertThat(resolved).isSameAs(engine);
    verify(agentProvider).get(agentConfig);
  }

  @Test
  void resolveEngineUsesRepositoryConfig() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final ConfigLoader configLoader = mock(ConfigLoader.class);
    final ConfigRepository configRepository = mock(ConfigRepository.class);
    final Agent engine = mock(Agent.class);
    final AgentConfig config = buildValidAgentConfig();

    when(configRepository.loadAgentConfig("agent")).thenReturn(config);
    when(agentProvider.get(config)).thenReturn(engine);

    final AgentManager service = new AgentManager(agentProvider, configLoader, configRepository);

    final Agent resolved = service.getOrStartEngine("agent", null);

    assertThat(resolved).isSameAs(engine);
    verify(configLoader, never()).loadConfig(any());
  }

  @Test
  void resolveEngineCachesByNameAndConfigPath() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final ConfigLoader configLoader = mock(ConfigLoader.class);
    final ConfigRepository configRepository = mock(ConfigRepository.class);
    final Agent engine = mock(Agent.class);
    final AgentConfig agentConfig = buildValidAgentConfig();

    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(agentConfig);
    when(agentProvider.get(agentConfig)).thenReturn(engine);

    final AgentManager service = new AgentManager(agentProvider, configLoader, configRepository);

    final Agent first = service.getOrStartEngine("agent", "config.json");
    final Agent second = service.getOrStartEngine("agent", "config.json");

    assertThat(first).isSameAs(second);
    verify(agentProvider).get(agentConfig);
  }

  @Test
  void resolveEngineRejectsMissingAgentId() {
    final AgentManager service = new AgentManager(mock(AgentProvider.class), mock(ConfigLoader.class),
        mock(ConfigRepository.class));

    assertThatThrownBy(() -> service.getOrStartEngine(" ", "config.json")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("agentId");
  }

  @Test
  void resolveEngineRejectsMissingConfig() {
    final AgentProvider agentProvider = mock(AgentProvider.class);
    final ConfigLoader configLoader = mock(ConfigLoader.class);
    final ConfigRepository configRepository = mock(ConfigRepository.class);

    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(null);

    final AgentManager service = new AgentManager(agentProvider, configLoader, configRepository);

    assertThatThrownBy(() -> service.getOrStartEngine("agent", "config.json"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("agentId");
  }

  private static AgentConfig buildValidAgentConfig() {
    final HybridAgentConfig config = new HybridAgentConfig();
    config.setAgentId("agent");
    config.setModel(model("reasoner.json"));
    config.setRouterModel(model("router.json"));
    config.setPlanningModel(model("planner.json"));
    return config;
  }

  private static AgentModelConfig model(final String id) {
    final AgentModelConfig config = new AgentModelConfig();
    config.setModelId(id);
    return config;
  }
}

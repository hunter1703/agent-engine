package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ConfigLoader;
import com.agentengine.engine.api.beans.config.HybridEngineConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.api.builders.AgentBuilderFactory;
import com.agentengine.interfaces.rest.services.AGUIAgent;
import com.agentengine.interfaces.rest.services.AgentManager;
import org.junit.jupiter.api.Test;

class AgentManagerTest {

  @Test
  void resolveEngineUsesProvidedNameAndConfigPath() {
    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    ConfigLoader configLoader = mock(ConfigLoader.class);
    ConfigRepository configRepository = mock(ConfigRepository.class);
    AgentBuilder builder = mock(AgentBuilder.class);
    Agent engine = mock(Agent.class);
    AgentConfig agentConfig = buildValidAgentConfig();

    when(builderFactory.getBuilder("hybrid")).thenReturn(builder);
    when(builder.build(eq("agent"), any())).thenReturn(engine);
    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(agentConfig);
    when(configRepository.loadModelConfig(any())).thenReturn(null);

    AgentManager service = new AgentManager(builderFactory, configLoader, configRepository);

    AGUIAgent resolved = service.getOrStartEngine("agent", "config.json");

    assertThat(resolved).isNotNull();
    verify(builder).build(eq("agent"), any());
  }

  @Test
  void resolveEngineUsesRepositoryConfig() {
    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    ConfigLoader configLoader = mock(ConfigLoader.class);
    ConfigRepository configRepository = mock(ConfigRepository.class);
    AgentBuilder builder = mock(AgentBuilder.class);
    Agent engine = mock(Agent.class);
    AgentConfig config = buildValidAgentConfig();

    when(configRepository.loadAgentConfig("agent")).thenReturn(config);
    when(builderFactory.getBuilder("hybrid")).thenReturn(builder);
    when(builder.build(eq("agent"), any())).thenReturn(engine);
    when(configRepository.loadModelConfig(any())).thenReturn(null);

    AgentManager service = new AgentManager(builderFactory, configLoader, configRepository);

    AGUIAgent resolved = service.getOrStartEngine("agent", null);

    assertThat(resolved).isNotNull();
    verify(configLoader, never()).loadConfig(any());
  }

  @Test
  void resolveEngineCachesByNameAndConfigPath() {
    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    ConfigLoader configLoader = mock(ConfigLoader.class);
    ConfigRepository configRepository = mock(ConfigRepository.class);
    AgentBuilder builder = mock(AgentBuilder.class);
    Agent engine = mock(Agent.class);
    AgentConfig agentConfig = buildValidAgentConfig();

    when(builderFactory.getBuilder("hybrid")).thenReturn(builder);
    when(builder.build(eq("agent"), any())).thenReturn(engine);
    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(agentConfig);
    when(configRepository.loadModelConfig(any())).thenReturn(null);

    AgentManager service = new AgentManager(builderFactory, configLoader, configRepository);

    AGUIAgent first = service.getOrStartEngine("agent", "config.json");
    AGUIAgent second = service.getOrStartEngine("agent", "config.json");

    assertThat(first).isSameAs(second);
  }

  @Test
  void resolveEngineRejectsMissingAgentName() {
    AgentManager service = new AgentManager(mock(AgentBuilderFactory.class), mock(ConfigLoader.class),
        mock(ConfigRepository.class));

    assertThatThrownBy(() -> service.getOrStartEngine(" ", "config.json")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("agentName");
  }

  @Test
  void resolveEngineRejectsMissingConfig() {
    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    ConfigLoader configLoader = mock(ConfigLoader.class);
    ConfigRepository configRepository = mock(ConfigRepository.class);

    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(null);

    AgentManager service = new AgentManager(builderFactory, configLoader, configRepository);

    assertThatThrownBy(() -> service.getOrStartEngine("agent", "config.json"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("agentName");
  }

  private static AgentConfig buildValidAgentConfig() {
    AgentConfig config = AgentConfig.empty();
    HybridEngineConfig engine = (HybridEngineConfig) config.getEngine();
    engine.setReasoning("reasoner.json");
    engine.setTool("tool.json");
    engine.setSystemPrompt("You are helpful.");
    return config;
  }
}

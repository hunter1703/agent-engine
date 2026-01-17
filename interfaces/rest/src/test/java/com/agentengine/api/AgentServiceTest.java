package com.agentengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ConfigLoader;
import com.agentengine.engine.builders.AgentBuilder;
import com.agentengine.engine.builders.AgentBuilderFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

  @org.junit.jupiter.api.AfterEach
  void clearPluginDir() {
    System.clearProperty("PLUGIN_DIR");
  }

  @Test
  void resolveEngineUsesProvidedNameAndConfigPath() {
    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    ConfigLoader configLoader = mock(ConfigLoader.class);
    AgentBuilder builder = mock(AgentBuilder.class);
    AgentEngine engine = mock(AgentEngine.class);

    when(builderFactory.getBuilder("agent")).thenReturn(builder);
    when(builder.build(eq("agent"), any())).thenReturn(engine);
    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(AgentConfig.empty());

    AgentService service = new AgentService(builderFactory, configLoader);

    AgentEngine resolved = service.getOrStartEngine("agent", "config.json");

    assertThat(resolved).isSameAs(engine);
    verify(builder).build(eq("agent"), any());
  }

  @Test
  void resolveEngineUsesPluginConfigDirectoryByDefault(
      @org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    ConfigLoader configLoader = mock(ConfigLoader.class);
    AgentBuilder builder = mock(AgentBuilder.class);
    AgentEngine engine = mock(AgentEngine.class);

    Path configDir = tempDir.resolve("config");
    java.nio.file.Files.createDirectories(configDir);
    Path configPath = configDir.resolve("agent.json");
    java.nio.file.Files.writeString(configPath, "{}\n");
    System.setProperty("PLUGIN_DIR", tempDir.toString());

    when(builderFactory.getBuilder("agent")).thenReturn(builder);
    when(builder.build(eq("agent"), any())).thenReturn(engine);
    when(configLoader.loadConfig(configPath)).thenReturn(AgentConfig.empty());

    AgentService service = new AgentService(builderFactory, configLoader);

    AgentEngine resolved = service.getOrStartEngine("agent", null);

    assertThat(resolved).isSameAs(engine);
  }

  @Test
  void resolveEngineCachesByNameAndConfigPath() {
    AgentBuilderFactory builderFactory = mock(AgentBuilderFactory.class);
    ConfigLoader configLoader = mock(ConfigLoader.class);
    AgentBuilder builder = mock(AgentBuilder.class);
    AgentEngine engine = mock(AgentEngine.class);

    when(builderFactory.getBuilder("agent")).thenReturn(builder);
    when(builder.build(eq("agent"), any())).thenReturn(engine);
    when(configLoader.loadConfig(Paths.get("config.json"))).thenReturn(AgentConfig.empty());

    AgentService service = new AgentService(builderFactory, configLoader);

    AgentEngine first = service.getOrStartEngine("agent", "config.json");
    AgentEngine second = service.getOrStartEngine("agent", "config.json");

    assertThat(first).isSameAs(second);
  }

  @Test
  void resolveEngineRejectsMissingAgentName() {
    AgentService service =
        new AgentService(mock(AgentBuilderFactory.class), mock(ConfigLoader.class));

    assertThatThrownBy(() -> service.getOrStartEngine(" ", "config.json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("agentName");
  }
}

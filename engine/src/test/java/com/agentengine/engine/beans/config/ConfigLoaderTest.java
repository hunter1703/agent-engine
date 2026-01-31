package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.engine.api.beans.config.AgentConfig;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.agentengine.engine.config.ConfigLoaderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

  @TempDir
  Path tempDir;

  @Test
  void loadConfigReadsJsonAndValidates() throws Exception {
    Path configPath = tempDir.resolve("agent.json");
    Files.writeString(configPath,
        "{" + "\"type\":\"hybrid\"," + "\"agentId\":\"agent\"," +
            "\"model\":{\"modelId\":\"reasoner\",\"systemPrompt\":\"system\"}," +
            "\"routerModel\":{\"modelId\":\"router\",\"systemPrompt\":\"system\"}," +
            "\"planningModel\":{\"modelId\":\"planner\",\"systemPrompt\":\"system\"}" + "}");

    AgentConfig config = new ConfigLoaderImpl().loadConfig(configPath);

    assertThat(config.getAgentId()).isEqualTo("agent");
  }

  @Test
  void loadConfigReadsYamlConfig() throws Exception {
    Path configPath = tempDir.resolve("agent.yml");
    Files.writeString(configPath,
        "type: hybrid\nagentId: agent\nmodel:\n  modelId: reasoner\n  systemPrompt: system\nrouterModel:\n  modelId: router\n  systemPrompt: system\nplanningModel:\n  modelId: planner\n  systemPrompt: system\n");

    AgentConfig config = new ConfigLoaderImpl().loadConfig(configPath);

    assertThat(config.getAgentId()).isEqualTo("agent");
  }

  @Test
  void loadConfigSupportsInMemoryStores() throws Exception {
    final Path configPath = tempDir.resolve("agent.json");
    Files.writeString(configPath, """
        {
          "type": "hybrid",
          "agentId": "agent",
          "model": {
            "modelId": "reasoner",
            "systemPrompt": "system",
            "contextManagerConfig": {
              "type": "last_n"
            }
          },
          "routerModel": {
            "modelId": "router",
            "systemPrompt": "router",
            "contextManagerConfig": {
              "type": "last_n"
            }
          },
          "planningModel": {
            "modelId": "planner",
            "systemPrompt": "planner",
            "contextManagerConfig": {
              "type": "last_n"
            }
          },
          "sessionStore": {
            "type": "memory"
          }
        }
        """);

    final AgentConfig config = new ConfigLoaderImpl().loadConfig(configPath);

    assertThat(config.getAgentId()).isEqualTo("agent");
    assertThat(config.getSessionStore().getType()).isEqualTo("memory");
  }

  @Test
  void loadConfigFailsWhenFileIsMissing() {
    Path missingPath = tempDir.resolve("missing.json");

    assertThatThrownBy(() -> new ConfigLoaderImpl().loadConfig(missingPath)).isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(FileNotFoundException.class);
  }
}

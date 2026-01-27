package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.engine.api.beans.config.AgentConfig;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

  @TempDir
  Path tempDir;

  @Test
  void loadConfigReadsJsonAndValidates() throws Exception {
    Path configPath = tempDir.resolve("agent.json");
    Files.writeString(configPath,
        "{" + "\"type\":\"hybrid\"," + "\"agentId\":\"agent\"," + "\"model\":{\"modelId\":\"reasoner\"},"
            + "\"routerModel\":{\"modelId\":\"router\"}," + "\"planningModel\":{\"modelId\":\"planner\"}" + "}");

    AgentConfig config = new ConfigLoaderImpl().loadConfig(configPath);

    assertThat(config.getAgentId()).isEqualTo("agent");
  }

  @Test
  void loadConfigReadsYamlConfig() throws Exception {
    Path configPath = tempDir.resolve("agent.yml");
    Files.writeString(configPath,
        "type: hybrid\nagentId: agent\nmodel:\n  modelId: reasoner\nrouterModel:\n  modelId: router\nplanningModel:\n  modelId: planner\n");

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
            "contextManagerConfig": {
              "type": "last_n",
              "systemPrompt": "system",
              "messageStore": {
                "type": "memory"
              }
            }
          },
          "routerModel": {
            "modelId": "router",
            "contextManagerConfig": {
              "type": "last_n",
              "systemPrompt": "router"
            }
          },
          "planningModel": {
            "modelId": "planner",
            "contextManagerConfig": {
              "type": "last_n",
              "systemPrompt": "planner"
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

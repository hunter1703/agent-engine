package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

  @TempDir Path tempDir;

  @Test
  void loadConfigReadsJsonAndValidatesEngineFields() throws Exception {
    Path configPath = tempDir.resolve("agent.json");
    Files.writeString(
        configPath,
        "{" + "\"engine\":{\"reasoning\":\"reasoner.json\",\"prompt\":\"You are helpful\"}" + "}");

    ConfigLoader loader = new ConfigLoader();
    AgentConfig config = loader.loadConfig(configPath);

    assertThat(config.getEngine().getReasoning()).isEqualTo("reasoner.json");
    assertThat(config.getEngine().getPrompt()).isEqualTo("You are helpful");
  }

  @Test
  void loadConfigReadsYamlConfig() throws Exception {
    Path configPath = tempDir.resolve("agent.yml");
    Files.writeString(configPath, "engine:\n  reasoning: reasoning.json\n  prompt: hello\n");

    ConfigLoader loader = new ConfigLoader();
    AgentConfig config = loader.loadConfig(configPath);

    assertThat(config.getEngine().getReasoning()).isEqualTo("reasoning.json");
    assertThat(config.getEngine().getPrompt()).isEqualTo("hello");
  }

  @Test
  void loadConfigFailsWhenFileIsMissing() {
    Path missingPath = tempDir.resolve("missing.json");

    ConfigLoader loader = new ConfigLoader();

    assertThatThrownBy(() -> loader.loadConfig(missingPath))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(java.io.FileNotFoundException.class);
  }
}


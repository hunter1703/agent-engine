package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ConfigLoaderImpl;
import com.agentengine.engine.api.beans.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

  @TempDir
  Path tempDir;

  @Test
  void loadConfigReadsJsonAndValidatesEngineFields() throws Exception {
    Path configPath = tempDir.resolve("agent.json");
    Files.writeString(configPath, "{"
        + "\"engine\":{\"type\":\"hybrid\",\"reasoningModelId\":\"reasoner.json\",\"systemPrompt\":\"You are helpful\",\"toolAssistantModelId\":\"tool.json\"}"
        + "}");

    ConfigLoader loader = new ConfigLoaderImpl();
    AgentConfig config = loader.loadConfig(configPath);

    assertThat(config.getEngine().getReasoningModelId()).isEqualTo("reasoner.json");
    assertThat(config.getEngine().getSystemPrompt()).isEqualTo("You are helpful");
  }

  @Test
  void loadConfigReadsYamlConfig() throws Exception {
    Path configPath = tempDir.resolve("agent.yml");
    Files.writeString(configPath,
        "engine:\n  type: hybrid\n  reasoningModelId: reasoning.json\n  systemPrompt: hello\n  toolAssistantModelId: tool.json\n");

    ConfigLoader loader = new ConfigLoaderImpl();
    AgentConfig config = loader.loadConfig(configPath);

    assertThat(config.getEngine().getReasoningModelId()).isEqualTo("reasoning.json");
    assertThat(config.getEngine().getSystemPrompt()).isEqualTo("hello");
  }

  @Test
  void loadConfigDefaultsToJsonWhenExtensionMissing() throws Exception {
    Path configPath = tempDir.resolve("agent");
    Files.writeString(configPath, "{"
        + "\"engine\":{\"type\":\"hybrid\",\"reasoningModelId\":\"reasoner.json\",\"systemPrompt\":\"Hello\",\"toolAssistantModelId\":\"tool.json\"}"
        + "}");

    ConfigLoader loader = new ConfigLoaderImpl();
    AgentConfig config = loader.loadConfig(configPath);

    assertThat(config.getEngine().getSystemPrompt()).isEqualTo("Hello");
  }

  @Test
  void loadConfigFailsWhenFileIsMissing() {
    Path missingPath = tempDir.resolve("missing.json");

    ConfigLoader loader = new ConfigLoaderImpl();

    assertThatThrownBy(() -> loader.loadConfig(missingPath)).isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(FileNotFoundException.class);
  }
}

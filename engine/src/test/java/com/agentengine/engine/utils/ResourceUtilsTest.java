package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.config.ModelConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceUtilsTest {

  @TempDir
  Path tempDir;

  @AfterEach
  void clearConfigDir() {
    System.clearProperty("CONFIG_DIR");
  }

  @Test
  void loadResourceAsStringReturnsEmptyForMissingResource() {
    assertThat(ResourceUtils.loadResourceAsString("/nope.txt")).isEmpty();
  }

  @Test
  void loadResourceAsStringReadsExistingResource() {
    String content = ResourceUtils.loadResourceAsString("/prompts/shared/router.txt");

    assertThat(content).contains("routing assistant");
  }

  @Test
  void loadModelConfigReadsJsonAndYamlFiles() throws Exception {
    Path jsonPath = tempDir.resolve("model.json");
    Files.writeString(jsonPath, "{\"provider\":\"OPEN_AI\",\"model\":\"gpt\"}");

    Path yamlPath = tempDir.resolve("model.yaml");
    Files.writeString(yamlPath, "provider: OLLAMA\nmodel: llama\n");

    ModelConfig jsonConfig = ResourceUtils.loadModelConfig(jsonPath.toString());
    ModelConfig yamlConfig = ResourceUtils.loadModelConfig(yamlPath.toString());

    assertThat(jsonConfig.getProvider()).isEqualTo("OPEN_AI");
    assertThat(yamlConfig.getProvider()).isEqualTo("OLLAMA");
  }

  @Test
  void loadModelConfigResolvesFromConfigDirectory() throws Exception {
    Path modelsDir = tempDir.resolve("models");
    Files.createDirectories(modelsDir);
    Path jsonPath = modelsDir.resolve("model.json");
    Files.writeString(jsonPath, "{\"provider\":\"OPEN_AI\",\"model\":\"gpt\"}");
    System.setProperty("CONFIG_DIR", tempDir.toString());

    ModelConfig config = ResourceUtils.loadModelConfig("model.json");

    assertThat(config.getProvider()).isEqualTo("OPEN_AI");
  }
}

package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelConfigValidatorTest {
  @TempDir Path tempDir;

  @Test
  void validateReturnsErrorsForMissingFields() throws Exception {
    Path path = tempDir.resolve("bad.json");
    Files.writeString(path, "{}\n");

    List<String> errors = ModelConfigValidator.validate(path);

    assertThat(errors).anyMatch(error -> error.contains("provider"));
    assertThat(errors).anyMatch(error -> error.contains("model"));
  }

  @Test
  void validateAcceptsValidConfig() throws Exception {
    Path path = tempDir.resolve("ok.json");
    Files.writeString(
        path,
        "{\"provider\":\"OLLAMA\",\"model\":\"qwen\",\"response_format\":\"text\"}\n");

    List<String> errors = ModelConfigValidator.validate(path);

    assertThat(errors).isEmpty();
  }
}

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
        path, "{\"provider\":\"OLLAMA\",\"model\":\"qwen\",\"response_format\":\"text\"}\n");

    List<String> errors = ModelConfigValidator.validate(path);

    assertThat(errors).isEmpty();
  }

  @Test
  void validateFlagsInvalidProviderAndResponseFormat() throws Exception {
    Path path = tempDir.resolve("invalid.json");
    Files.writeString(
        path, "{\"provider\":\"BAD\",\"model\":\"model\",\"response_format\":\"xml\"}\n");

    List<String> errors = ModelConfigValidator.validate(path);

    assertThat(errors).anyMatch(error -> error.contains("provider must"));
    assertThat(errors).anyMatch(error -> error.contains("response_format"));
  }

  @Test
  void validateRequiresThoughtTagsWhenEnabled() throws Exception {
    Path path = tempDir.resolve("thoughts.json");
    Files.writeString(
        path, "{\"provider\":\"OPEN_AI\",\"model\":\"gpt\",\"thoughts_enabled\":true}\n");

    List<String> errors = ModelConfigValidator.validate(path);

    assertThat(errors).anyMatch(error -> error.contains("thoughts_start_tag"));
    assertThat(errors).anyMatch(error -> error.contains("thoughts_end_tag"));
  }

  @Test
  void validateReturnsErrorWhenConfigMissing() {
    Path path = tempDir.resolve("missing.json");

    List<String> errors = ModelConfigValidator.validate(path);

    assertThat(errors).anyMatch(error -> error.contains("Unable to read model config"));
  }
}

package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

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

}

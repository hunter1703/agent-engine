package com.agentengine.engine.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigJsonTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void modelConfigs_areValidJson() throws IOException {
    assertJsonConfigs("models");
  }

  @Test
  void agentConfigs_areValidJson() throws IOException {
    assertJsonConfigs("agents");
  }

  private static void assertJsonConfigs(final String directory) throws IOException {
    final Path configsRoot = resolveConfigsRoot();
    final Path configDir = configsRoot.resolve(directory);
    assertThat(Files.isDirectory(configDir)).isTrue();

    final List<Path> configFiles;
    try (Stream<Path> files = Files.list(configDir)) {
      configFiles = files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
    }

    assertThat(configFiles).isNotEmpty();
    for (final Path configFile : configFiles) {
      assertThatCode(() -> MAPPER.readTree(configFile.toFile())).as("config file %s", configFile)
          .doesNotThrowAnyException();
    }
  }

  private static Path resolveConfigsRoot() {
    final Path startingPath = Path.of("").toAbsolutePath();
    Path current = startingPath;
    for (int i = 0; i < 5 && current != null; i++) {
      final Path candidate = current.resolve("configs");
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("configs directory not found from " + startingPath);
  }
}

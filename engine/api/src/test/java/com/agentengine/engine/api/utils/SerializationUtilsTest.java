package com.agentengine.engine.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SerializationUtilsTest {

  @TempDir
  Path tempDir;

  @Test
  void jsonFromFileReadsPayload() throws Exception {
    final Path path = tempDir.resolve("payload.json");
    Files.writeString(path, "{\"name\":\"value\"}");

    final Map<String, Object> payload = JsonUtils.fromFile(path, new TypeReference<>() {
    });

    assertThat(payload).containsEntry("name", "value");
  }

  @Test
  void yamlFromFileReadsPayload() throws Exception {
    final Path path = tempDir.resolve("payload.yml");
    Files.writeString(path, "name: value\n");

    @SuppressWarnings("unchecked")
    final Map<String, Object> payload = YamlUtils.fromFile(path, Map.class);

    assertThat(payload).containsEntry("name", "value");
  }
}

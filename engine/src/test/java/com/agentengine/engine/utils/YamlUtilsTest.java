package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlUtilsTest {

  @TempDir
  Path tempDir;

  @Test
  void toMapReturnsNullOnBlank() {
    assertThat(YamlUtils.toMap(" ")).isNull();
  }

  @Test
  void toMapParsesYaml() {
    Map<String, Object> parsed = YamlUtils.toMap("name: test\ncount: 2\n");

    assertThat(parsed).containsEntry("name", "test").containsEntry("count", 2);
  }

  @Test
  void fromFileReadsYaml() throws Exception {
    Path path = tempDir.resolve("data.yaml");
    Files.writeString(path, "name: example\n");

    Map<String, Object> parsed = YamlUtils.fromFile(path, Map.class);

    assertThat(parsed).containsEntry("name", "example");
  }
}

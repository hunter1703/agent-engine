package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.config.ModelConfig;
import com.alibaba.fastjson2.TypeReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonUtilsTest {

  @TempDir Path tempDir;

  @Test
  void fromJsonReturnsNullOnBlank() {
    assertThat(JsonUtils.fromJson("", Map.class)).isNull();
  }

  @Test
  void toMapReturnsNullWhenInputStreamMissing() {
    assertThat(JsonUtils.toMap(null)).isNull();
  }

  @Test
  void fromJsonParsesJsonString() {
    Map<String, Object> parsed = JsonUtils.fromJson("{\"value\":42}", Map.class);

    assertThat(parsed).containsEntry("value", 42);
  }

  @Test
  void fromFileReadsJson() throws Exception {
    Path path = tempDir.resolve("data.json");
    Files.writeString(path, "{\"name\":\"test\"}");

    Map<String, Object> parsed = JsonUtils.fromFile(path, Map.class);

    assertThat(parsed).containsEntry("name", "test");
  }

  @Test
  void fromFileTypeReferenceReadsJson() throws Exception {
    Path path = tempDir.resolve("data-map.json");
    Files.writeString(path, "{\"count\":3}");

    Map<String, Object> parsed =
        JsonUtils.fromFile(path, new TypeReference<>() {});

    assertThat(parsed).containsEntry("count", 3);
  }

  @Test
  void fromMapConvertsToTargetClass() {
    Map<String, Object> data = Map.of("provider", "OPEN_AI", "model", "gpt");

    ModelConfig config = JsonUtils.fromMap(data, ModelConfig.class);

    assertThat(config.getProvider()).isEqualTo("OPEN_AI");
    assertThat(config.getModel()).isEqualTo("gpt");
  }

  @Test
  void fromJsonTypeReferenceParsesMap() {
    Map<String, Object> parsed =
        JsonUtils.fromJson("{\"value\":42}", new TypeReference<>() {});

    assertThat(parsed).containsEntry("value", 42);
  }

  @Test
  void toMapParsesInputStream() throws Exception {
    Path path = tempDir.resolve("stream.json");
    Files.writeString(path, "{\"key\":\"value\"}");

    try (var stream = Files.newInputStream(path)) {
      Map<String, Object> parsed = JsonUtils.toMap(stream);

      assertThat(parsed).containsEntry("key", "value");
    }
  }

  @Test
  void toJsonSerializesMap() {
    String json = JsonUtils.toJson(Map.of("key", "value"));

    assertThat(json).contains("\"key\"").contains("\"value\"");
  }
}

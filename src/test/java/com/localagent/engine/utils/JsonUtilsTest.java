package com.localagent.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void fromJsonReturnsNullOnBlank() {
        assertThat(JsonUtils.fromJson("", Map.class)).isNull();
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
    void toJsonSerializesMap() {
        String json = JsonUtils.toJson(Map.of("key", "value"));

        assertThat(json).contains("\"key\"").contains("\"value\"");
    }
}

package com.localagent.engine;

import com.localagent.engine.config.ConfigLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {
    @Test
    void loadsYamlConfig(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("config.yaml");
        Files.writeString(
            path,
            "engine:\n  prompt: hello\n  reasoning: demo\ncontext:\n  type: last_n\n  keep_last: 5\nstate_store:\n  type: memory"
        );
        Map<String, Object> data = ConfigLoader.loadConfig(path);
        assertThat(data).containsKey("engine");
    }
}

package com.localagent.engine;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRegistryTest {
    @Test
    void loadsModelConfig() {
        Map<String, Object> config = ModelRegistry.loadModelConfig("qwq-32b");
        assertThat(config).containsKey("model");
    }
}

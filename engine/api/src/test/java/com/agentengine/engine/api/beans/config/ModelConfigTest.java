package com.agentengine.engine.api.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class ModelConfigTest {

  @Test
  void loadConfigReadsTypeField() {
    final String json = """
        {
          "type": "LLAMA_CPP",
          "model": "test",
          "responseFormat": "text"
        }
        """;

    final ModelConfig config = JsonUtils.fromJson(json, ModelConfig.class);

    assertThat(config.getType()).isEqualTo("LLAMA_CPP");
    assertThat(config.getModel()).isEqualTo("test");
  }
}

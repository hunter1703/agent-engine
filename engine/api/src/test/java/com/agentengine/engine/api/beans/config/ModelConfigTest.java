package com.agentengine.engine.api.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Test
  void validateRequiresType() {
    final ModelConfig config = new ModelConfig();
    config.setModel("test");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type");
  }

  @Test
  void validateRequiresModel() {
    final ModelConfig config = new ModelConfig();
    config.setType("OPEN_AI");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("model");
  }
}

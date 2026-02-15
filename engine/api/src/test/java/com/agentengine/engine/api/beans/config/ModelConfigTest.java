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
          "type": "open_ai_compatible",
          "model": "test"
        }
        """;

    final ModelConfig config = JsonUtils.fromJson(json, ModelConfig.class);

    assertThat(config.getType()).isEqualTo("open_ai_compatible");
    assertThat(config.getModel()).isEqualTo("test");
  }

  @Test
  void loadConfigReadsGeminiApiKey() {
    final String json = """
        {
          "type": "gemini",
          "model": "gemini-2.0-flash",
          "apiKey": "secret"
        }
        """;

    final ModelConfig config = JsonUtils.fromJson(json, ModelConfig.class);

    assertThat(config.getApiKey()).isEqualTo("secret");
  }

  @Test
  void setTypeNormalizesToLowerCase() {
    final ModelConfig config = new ModelConfig();

    config.setType("OPEN_AI_COMPATIBLE");

    assertThat(config.getType()).isEqualTo("open_ai_compatible");
  }

  @Test
  void validateRequiresType() {
    final ModelConfig config = new ModelConfig();
    config.setModel("test");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
  }

  @Test
  void validateRequiresModel() {
    final ModelConfig config = new ModelConfig();
    config.setType("open_ai_compatible");

    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("model");
  }
}

package com.agentengine.engine.api.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelConfigTest {

  @Test
  void shouldNormalizeTypeWhenSetTypeCalled() {
    final ModelConfig modelConfig = new ModelConfig();

    modelConfig.setType("  OPEN_AI_COMPATIBLE  ");

    assertThat(modelConfig.getType()).isEqualTo("open_ai_compatible");
  }

  @Test
  void shouldReturnUnknownProviderWhenTypeBlank() {
    assertThat(ModelConfig.Provider.valueOfOrDefault(" ")).isEqualTo(ModelConfig.Provider.UNKNOWN);
  }

  @Test
  void shouldReturnProviderWhenTypeKnown() {
    assertThat(ModelConfig.Provider.valueOfOrDefault("ollama"))
        .isEqualTo(ModelConfig.Provider.OLLAMA);
  }

  @Test
  void shouldThrowWhenProviderTypeUnsupportedInFromType() {
    assertThatThrownBy(() -> ModelConfig.Provider.fromType("unsupported"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported model provider");
  }

  @Test
  void shouldMatchProviderWhenTypeNormalized() {
    assertThat(ModelConfig.Provider.OPEN_AI_COMPATIBLE.matches("OPEN_AI_COMPATIBLE")).isTrue();
  }
}

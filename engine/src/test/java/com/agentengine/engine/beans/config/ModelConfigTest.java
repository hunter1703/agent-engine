package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelConfigTest {

  @Test
  void settersPopulateFields() {
    ModelConfig config = new ModelConfig();
    config.setProvider("OPEN_AI");
    config.setModel("gpt");
    config.setTemperature(0.2);
    config.setTopK(5);
    config.setTopP(0.9);
    config.setRepeatPenalty(1.1);
    config.setNumPredict(42);
    config.setMaxContextLength(2048);
    config.setStopTokens(List.of("STOP"));
    config.setResponseFormat("json");
    config.setThoughtsEnabled(true);
    config.setThoughtsStartTag("<think>");
    config.setThoughtsEndTag("</think>");
    config.setContextConfig(new LastNContextConfig());

    assertThat(config.getProvider()).isEqualTo("OPEN_AI");
    assertThat(config.getModel()).isEqualTo("gpt");
    assertThat(config.getTemperature()).isEqualTo(0.2);
    assertThat(config.getTopK()).isEqualTo(5);
    assertThat(config.getTopP()).isEqualTo(0.9);
    assertThat(config.getRepeatPenalty()).isEqualTo(1.1);
    assertThat(config.getNumPredict()).isEqualTo(42);
    assertThat(config.getMaxContextLength()).isEqualTo(2048);
    assertThat(config.getStopTokens()).containsExactly("STOP");
    assertThat(config.getResponseFormat()).isEqualTo("json");
    assertThat(config.isThoughtsEnabled()).isTrue();
    assertThat(config.getThoughtsStartTag()).isEqualTo("<think>");
    assertThat(config.getThoughtsEndTag()).isEqualTo("</think>");
    assertThat(config.getContextConfig()).isInstanceOf(LastNContextConfig.class);
  }

  @Test
  void providerEnumResolvesValue() {
    assertThat(ModelConfig.Provider.valueOf("OPEN_AI")).isEqualTo(ModelConfig.Provider.OPEN_AI);
  }
}


package com.agentengine.engine.builders.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.ModelConfig;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import org.junit.jupiter.api.Test;

class LangchainModelBuilderTest {

  @Test
  void defaultsToJsonWhenParsingToolCallsFromText() {
    final ModelConfig config = new ModelConfig();
    config.setToolCallingEnabled(true);
    config.setToolCallingSupported(false);

    final ResponseFormat format = LangchainModelBuilder.getResponseFormat(config);

    assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
  }

  @Test
  void defaultsToTextWhenUsingNativeToolCalling() {
    final ModelConfig config = new ModelConfig();
    config.setToolCallingEnabled(true);
    config.setToolCallingSupported(true);

    final ResponseFormat format = LangchainModelBuilder.getResponseFormat(config);

    assertThat(format.type()).isEqualTo(ResponseFormatType.TEXT);
  }

  @Test
  void defaultsToTextWhenToolCallingDisabled() {
    final ModelConfig config = new ModelConfig();
    config.setToolCallingEnabled(false);
    config.setToolCallingSupported(false);

    final ResponseFormat format = LangchainModelBuilder.getResponseFormat(config);

    assertThat(format.type()).isEqualTo(ResponseFormatType.TEXT);
  }

  @Test
  void honorsExplicitResponseFormatOverride() {
    final ModelConfig config = new ModelConfig();
    config.setResponseFormat("json");
    config.setToolCallingEnabled(false);
    config.setToolCallingSupported(true);

    final ResponseFormat format = LangchainModelBuilder.getResponseFormat(config);

    assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
  }
}

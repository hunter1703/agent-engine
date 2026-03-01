package com.agentengine.engine.builders.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.model.DelegatingLLMModel;
import com.agentengine.engine.agents.processors.Parser;
import org.junit.jupiter.api.Test;

class GeminiModelBuilderTest {

  @Test
  void buildsGeminiModelWithToolCallingConfig() {
    final ModelConfig modelConfig = new ModelConfig();
    modelConfig.setType(ModelConfig.Provider.GEMINI.type());
    modelConfig.setModel("gemini-2.0-flash");
    modelConfig.setApiKey("test-key");
    modelConfig.setToolCallingEnabled(true);
    modelConfig.setToolCallingSupported(true);

    final GeminiModelBuilder builder = new GeminiModelBuilder();

    final DelegatingLLMModel model = builder.build(modelConfig);

    assertThat(model.model()).isEqualTo("gemini-2.0-flash");
    assertThat(model.isToolCallingEnabled()).isTrue();
    assertThat(model.isParseToolCallsFromText()).isFalse();
  }
}

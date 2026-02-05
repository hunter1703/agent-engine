package com.agentengine.engine.builders.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.model.DelegatingLLMModel;
import com.agentengine.engine.utils.Parser;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiModelBuilderTest {

  @Test
  void buildsGeminiModelWithToolCallingConfig() {
    final ModelConfig modelConfig = new ModelConfig();
    modelConfig.setType(ModelConfig.Provider.GEMINI.name());
    modelConfig.setModel("gemini-2.0-flash");
    modelConfig.setApiKey("test-key");
    modelConfig.setToolCallingEnabled(true);
    modelConfig.setToolCallingSupported(true);

    final ConfigRepository configRepository = new ConfigRepository() {
      @Override
      public AgentConfig loadAgentConfig(final String agentId) {
        return null;
      }

      @Override
      public ModelConfig loadModelConfig(final String modelId) {
        return modelConfig;
      }

      @Override
      public List<String> listAgentConfigs() {
        return List.of();
      }
    };

    final GeminiModelBuilder builder = new GeminiModelBuilder(configRepository);
    final AgentModelConfig agentModelConfig = new AgentModelConfig();
    agentModelConfig.setModelId("gemini");

    final DelegatingLLMModel model = builder.build("agent-id", agentModelConfig);

    assertThat(model.model()).isEqualTo("gemini-2.0-flash");
    assertThat(model.isToolCallingEnabled()).isTrue();
    assertThat(model.isParseToolCallsFromText()).isFalse();
    assertThat(model.getRequestProcessors()).anyMatch(Parser.class::isInstance);
  }
}

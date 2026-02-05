package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.models.Gemini;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import jakarta.inject.Singleton;

@Singleton
public class GeminiModelBuilder extends DelegatingModelBuilder<Gemini> {

  public GeminiModelBuilder(final ConfigRepository configRepository) {
    super(configRepository);
  }

  @Override
  public String type() {
    return AgentModelConfig.ModelType.GEMINI.name().toLowerCase();
  }

  @Override
  protected Gemini buildDelegate(final ModelConfig modelConfig) {
    return Gemini.builder().modelName(modelConfig.getModel()).apiKey(modelConfig.getApiKey()).build();
  }
}

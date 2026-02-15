package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.google.adk.models.Gemini;
import jakarta.inject.Singleton;

@Singleton
public class GeminiModelBuilder extends DelegatingModelBuilder<Gemini> {

  @Override
  public String type() {
    return ModelConfig.Provider.GEMINI.type();
  }

  @Override
  protected Gemini buildDelegate(final ModelConfig modelConfig) {
    return Gemini.builder().modelName(modelConfig.getModel()).apiKey(modelConfig.getApiKey()).build();
  }
}

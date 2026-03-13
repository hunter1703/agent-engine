package com.agentengine.engine.factories.model;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.google.adk.models.Gemini;
import jakarta.inject.Singleton;

@Singleton
public class GeminiModelFactory extends DelegatingModelFactory<Gemini> {

  @Override
  public String type() {
    return ModelConfig.Provider.GEMINI.name();
  }

  @Override
  protected Gemini buildDelegate(final ModelConfig modelConfig) {
    return Gemini.builder().modelName(modelConfig.getModel()).apiKey(modelConfig.getApiKey()).build();
  }
}

package com.agentengine.engine.factories.model;

import com.agentengine.engine.api.beans.config.ModelConfig;
import jakarta.inject.Singleton;

@Singleton
public class OllamaModelFactory extends LangchainModelFactory {
  @Override
  public String type() {
    return ModelConfig.Provider.OLLAMA.name();
  }
}

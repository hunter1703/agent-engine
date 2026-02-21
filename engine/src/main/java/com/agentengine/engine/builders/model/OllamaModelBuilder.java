package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.beans.config.ModelConfig;
import jakarta.inject.Singleton;

@Singleton
public class OllamaModelBuilder extends LangchainModelBuilder {
  @Override
  public String type() {
    return ModelConfig.Provider.OLLAMA.type();
  }
}

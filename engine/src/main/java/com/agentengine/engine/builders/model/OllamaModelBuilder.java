package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.beans.config.ModelConfig;
import dev.langchain4j.model.chat.request.json.*;
import jakarta.inject.Singleton;

@Singleton
public class OllamaModelBuilder extends LangchainModelBuilder {
  @Override
  public String type() {
    return ModelConfig.Provider.OLLAMA.name().toLowerCase();
  }
}

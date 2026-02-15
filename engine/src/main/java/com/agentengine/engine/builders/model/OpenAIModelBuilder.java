package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.beans.config.ModelConfig;
import jakarta.inject.Singleton;

@Singleton
public class OpenAIModelBuilder extends LangchainModelBuilder {
  @Override
  public String type() {
    return ModelConfig.Provider.OPEN_AI_COMPATIBLE.type();
  }
}

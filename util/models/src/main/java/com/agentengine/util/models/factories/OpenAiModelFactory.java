package com.agentengine.util.models.factories;

import com.agentengine.util.agents.beans.config.ModelConfig;
import jakarta.inject.Singleton;

@Singleton
public class OpenAiModelFactory extends OpenAiCompatibleModelFactory {
  @Override
  public String type() {
    return ModelConfig.Provider.OPEN_AI.name();
  }
}

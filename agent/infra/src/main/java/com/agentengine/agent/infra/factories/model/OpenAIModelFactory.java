package com.agentengine.agent.infra.factories.model;

import com.agentengine.util.agents.beans.config.ModelConfig;
import jakarta.inject.Singleton;

@Singleton
public class OpenAIModelFactory extends LangchainModelFactory {
  @Override
  public String type() {
    return ModelConfig.Provider.OPEN_AI_COMPATIBLE.name();
  }
}

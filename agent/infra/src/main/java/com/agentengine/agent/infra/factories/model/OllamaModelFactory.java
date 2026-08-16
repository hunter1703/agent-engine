package com.agentengine.agent.infra.factories.model;

import com.agentengine.util.agents.beans.config.ModelConfig;
import jakarta.inject.Singleton;

@Singleton
public class OllamaModelFactory extends LangchainModelFactory {
  @Override
  public String type() {
    return ModelConfig.Provider.OLLAMA.name();
  }
}

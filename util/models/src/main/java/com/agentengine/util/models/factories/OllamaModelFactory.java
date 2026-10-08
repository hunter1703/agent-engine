package com.agentengine.util.models.factories;

import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.models.llm.ToolsOffMode;
import jakarta.inject.Singleton;

/**
 * Ollama, through its OpenAI-compatible endpoint (base URL ending in {@code /v1}). It ignores
 * {@code tool_choice}, so tools are switched off by leaving their definitions out of the request.
 */
@Singleton
public class OllamaModelFactory extends OpenAiCompatibleModelFactory {
  @Override
  public String type() {
    return ModelConfig.Provider.OLLAMA.name();
  }

  @Override
  protected ToolsOffMode toolsOffMode() {
    return ToolsOffMode.REMOVE_TOOL_DEFINITIONS;
  }
}

package com.agentengine.util.models.factories;

import com.agentengine.util.agents.beans.config.ChatModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.models.llm.ToolsOffMode;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * Z.ai. Its API is OpenAI-compatible apart from the {@code thinking} request field, which switches
 * its reasoning on or off together with the config's {@code thoughtsEnabled}, and its {@code
 * tool_choice}, which takes only {@code auto}.
 */
@Singleton
public class ZaiModelFactory extends OpenAiCompatibleModelFactory {
  @Override
  public String type() {
    return ModelConfig.Provider.Z_AI.name();
  }

  @Override
  protected Map<String, Object> additionalParams(final ChatModelConfig chatConfig) {
    return Map.of(
        "thinking", Map.of("type", chatConfig.isThoughtsEnabled() ? "enabled" : "disabled"));
  }

  @Override
  protected ToolsOffMode toolsOffMode() {
    return ToolsOffMode.REMOVE_TOOL_DEFINITIONS;
  }
}

package com.agentengine.util.models.factories;

import com.agentengine.util.models.config.ChatModelConfig;
import com.agentengine.util.models.config.ModelConfig;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * Z.ai. Its API is OpenAI-compatible apart from the {@code thinking} request field, which switches
 * its reasoning on or off together with the config's {@code thoughtsEnabled}.
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
}

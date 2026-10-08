package com.agentengine.util.models.factories;

import com.agentengine.util.agents.beans.config.ChatModelConfig;
import com.agentengine.util.agents.beans.config.KeyValuePair;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.llm.LangChain4jModel;
import com.agentengine.util.models.llm.ToolsOffMode;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// always builds models with  text response format, since model is a shared resource. If an agent
// specifies a response format, it will override at llm request level in link
// com.agentengine.util.models.llm.LangChain4jModel
public abstract class OpenAiCompatibleModelFactory extends DelegatingModelFactory<BaseLlm> {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

  @Override
  protected BaseLlm buildDelegate(final ChatModelConfig chatConfig) {
    return new LangChain4jModel(
        buildOpenAI(chatConfig),
        buildOpenAIStreaming(chatConfig),
        chatConfig.getModel(),
        toolsOffMode());
  }

  /**
   * How this provider is told not to call tools on a turn; see {@link ToolsOffMode}. The OpenAI
   * standard, {@code tool_choice: none}, unless the provider overrides it.
   */
  protected ToolsOffMode toolsOffMode() {
    return ToolsOffMode.SEND_TOOL_CHOICE_NONE;
  }

  /**
   * Request fields this provider always sends beyond the OpenAI standard; any the model config
   * sets in its own additional params take precedence.
   */
  protected Map<String, Object> additionalParams(final ChatModelConfig chatConfig) {
    return Map.of();
  }

  private ChatModel buildOpenAI(final ChatModelConfig config) {
    final OpenAiChatModel.OpenAiChatModelBuilder builder =
        OpenAiChatModel.builder()
            .httpClientBuilder(LangchainUtils.httpClientBuilder())
            .modelName(config.getModel())
            .baseUrl(config.getBaseUrl())
            .apiKey(config.getApiKey())
            .temperature(config.getTemperature())
            .topP(config.getTopP())
            .stop(config.getStopSequences())
            .maxTokens(config.getMaxOutputTokens())
            .frequencyPenalty(config.getFrequencyPenalty())
            .presencePenalty(config.getPresencePenalty())
            .returnThinking(config.isThoughtsEnabled())
            .timeout(DEFAULT_TIMEOUT);
    final Map<String, Object> customParams = requestParams(config);
    if (!customParams.isEmpty()) {
      builder.customParameters(customParams);
    }
    return builder.build();
  }

  private StreamingChatModel buildOpenAIStreaming(final ChatModelConfig config) {
    final OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder =
        OpenAiStreamingChatModel.builder()
            .httpClientBuilder(LangchainUtils.httpClientBuilder())
            .modelName(config.getModel())
            .baseUrl(config.getBaseUrl())
            .apiKey(config.getApiKey())
            .temperature(config.getTemperature())
            .topP(config.getTopP())
            .stop(config.getStopSequences())
            .maxTokens(config.getMaxOutputTokens())
            .frequencyPenalty(config.getFrequencyPenalty())
            .presencePenalty(config.getPresencePenalty())
            .returnThinking(config.isThoughtsEnabled())
            .timeout(DEFAULT_TIMEOUT);
    final Map<String, Object> customParams = requestParams(config);
    if (!customParams.isEmpty()) {
      builder.customParameters(customParams);
    }
    return builder.build();
  }

  private Map<String, Object> requestParams(final ChatModelConfig config) {
    final Map<String, Object> params = new LinkedHashMap<>(additionalParams(config));
    params.putAll(getParamsMap(config.getAdditionalParams()));
    return params;
  }

  private static Map<String, Object> getParamsMap(final List<KeyValuePair> pairs) {
    if (pairs == null || pairs.isEmpty()) {
      return Map.of();
    }
    final Map<String, Object> params = new LinkedHashMap<>();
    for (final KeyValuePair pair : pairs) {
      final String key = pair == null ? null : pair.getKey();
      final Object value = pair == null ? null : pair.getValue();
      if (StringUtils.isNotBlank(key) && value != null) {
        params.put(key, value);
      }
    }
    return Collections.unmodifiableMap(params);
  }
}

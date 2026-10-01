package com.agentengine.util.models.factories;

import com.agentengine.util.agents.beans.config.ChatModelConfig;
import com.agentengine.util.agents.beans.config.KeyValuePair;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.llm.LangChain4jModel;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
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
public abstract class LangchainModelFactory extends DelegatingModelFactory<BaseLlm> {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

  @Override
  protected BaseLlm buildDelegate(final ChatModelConfig chatConfig) {
    final ChatModels models = buildChatModels(chatConfig);
    return new LangChain4jModel(
        models.chatModel(), models.streamingChatModel(), chatConfig.getModel());
  }

  private record ChatModels(ChatModel chatModel, StreamingChatModel streamingChatModel) {}

  private static ChatModels buildChatModels(final ChatModelConfig chatConfig) {
    final ModelConfig.Provider provider = ModelConfig.Provider.fromType(chatConfig.getProvider());
    return switch (provider) {
      case ModelConfig.Provider.OLLAMA ->
          new ChatModels(buildOllama(chatConfig), buildOllamaStreaming(chatConfig));
      case ModelConfig.Provider.OPEN_AI_COMPATIBLE ->
          new ChatModels(buildOpenAI(chatConfig), buildOpenAIStreaming(chatConfig));
      default -> throw new IllegalArgumentException("Unsupported model provider: " + provider);
    };
  }

  private static ChatModel buildOllama(final ChatModelConfig config) {
    return OllamaChatModel.builder()
        .httpClientBuilder(LangchainUtils.httpClientBuilder())
        .modelName(config.getModel())
        .baseUrl(config.getBaseUrl())
        .temperature(config.getTemperature())
        .topK(config.getTopK())
        .topP(config.getTopP())
        .repeatPenalty(config.getRepeatPenalty())
        .numPredict(config.getNumPredict())
        .numCtx(config.getMaxContextLength())
        .stop(config.getStopTokens())
        .timeout(DEFAULT_TIMEOUT)
        .build();
  }

  private static ChatModel buildOpenAI(final ChatModelConfig config) {
    final OpenAiChatModel.OpenAiChatModelBuilder builder =
        OpenAiChatModel.builder()
            .httpClientBuilder(LangchainUtils.httpClientBuilder())
            .modelName(config.getModel())
            .baseUrl(config.getBaseUrl())
            .apiKey(config.getApiKey())
            .temperature(config.getTemperature())
            .topP(config.getTopP())
            .stop(config.getStopTokens())
            .returnThinking(config.isThoughtsEnabled())
            .timeout(DEFAULT_TIMEOUT);
    final Map<String, Object> customParams = getParamsMap(config.getAdditionalParams());
    if (!customParams.isEmpty()) {
      builder.customParameters(customParams);
    }
    return builder.build();
  }

  private static StreamingChatModel buildOllamaStreaming(final ChatModelConfig config) {
    return OllamaStreamingChatModel.builder()
        .httpClientBuilder(LangchainUtils.httpClientBuilder())
        .modelName(config.getModel())
        .baseUrl(config.getBaseUrl())
        .temperature(config.getTemperature())
        .topK(config.getTopK())
        .topP(config.getTopP())
        .repeatPenalty(config.getRepeatPenalty())
        .numPredict(config.getNumPredict())
        .numCtx(config.getMaxContextLength())
        .stop(config.getStopTokens())
        .timeout(DEFAULT_TIMEOUT)
        .build();
  }

  private static StreamingChatModel buildOpenAIStreaming(final ChatModelConfig config) {
    final OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder =
        OpenAiStreamingChatModel.builder()
            .httpClientBuilder(LangchainUtils.httpClientBuilder())
            .modelName(config.getModel())
            .baseUrl(config.getBaseUrl())
            .apiKey(config.getApiKey())
            .temperature(config.getTemperature())
            .topP(config.getTopP())
            .stop(config.getStopTokens())
            .returnThinking(config.isThoughtsEnabled())
            .timeout(DEFAULT_TIMEOUT);
    final Map<String, Object> customParams = getParamsMap(config.getAdditionalParams());
    if (!customParams.isEmpty()) {
      builder.customParameters(customParams);
    }
    return builder.build();
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

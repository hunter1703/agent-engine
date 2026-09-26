package com.agentengine.util.models.factories;

import com.agentengine.util.agents.beans.config.ChatModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.models.llm.LangChain4jModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import java.util.Map;

public abstract class LangchainModelFactory extends DelegatingModelFactory<BaseLlm> {
  private static final Map<String, Object> DEFAULT_JSON_RESPONSE_FORMAT;
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

  static {
    DEFAULT_JSON_RESPONSE_FORMAT =
        JsonUtils.fromJson(
            ResourceUtils.loadResourceAsString("/schemas/shared/response_schema.json"),
            new TypeReference<>() {});
  }

  @Override
  protected BaseLlm buildDelegate(final ChatModelConfig chatConfig) {
    final ResponseFormatType responseFormatType = resolveResponseFormatType(chatConfig);
    final ResponseFormat responseFormat = getResponseFormat(responseFormatType);
    final ChatModels models = buildChatModels(chatConfig, responseFormat);
    return new LangChain4jModel(
        models.chatModel(), models.streamingChatModel(), chatConfig.getModel());
  }

  private record ChatModels(
      ChatModel chatModel, StreamingChatModel streamingChatModel, ResponseFormat responseFormat) {}

  private static ChatModels buildChatModels(
      final ChatModelConfig chatConfig, final ResponseFormat responseFormat) {
    final ModelConfig.Provider provider = ModelConfig.Provider.fromType(chatConfig.getProvider());
    return switch (provider) {
      case ModelConfig.Provider.OLLAMA ->
          new ChatModels(
              buildOllama(chatConfig, responseFormat),
              buildOllamaStreaming(chatConfig, responseFormat),
              responseFormat);
      case ModelConfig.Provider.OPEN_AI_COMPATIBLE -> {
        yield new ChatModels(
            buildOpenAI(chatConfig, responseFormat),
            buildOpenAIStreaming(chatConfig, responseFormat),
            responseFormat);
      }
      default -> throw new IllegalArgumentException("Unsupported model provider: " + provider);
    };
  }

  private static ChatModel buildOllama(
      final ChatModelConfig config, final ResponseFormat responseFormat) {
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
        .responseFormat(responseFormat)
        .timeout(DEFAULT_TIMEOUT)
        .build();
  }

  private static ChatModel buildOpenAI(
      final ChatModelConfig config, final ResponseFormat responseFormat) {
    final String format = responseFormat.type() == ResponseFormatType.JSON ? "json" : null;
    return OpenAiChatModel.builder()
        .httpClientBuilder(LangchainUtils.httpClientBuilder())
        .modelName(config.getModel())
        .baseUrl(config.getBaseUrl())
        .apiKey(config.getApiKey())
        .temperature(config.getTemperature())
        .topP(config.getTopP())
        .stop(config.getStopTokens())
        .responseFormat(format)
        .returnThinking(config.isThoughtsEnabled())
        .timeout(DEFAULT_TIMEOUT)
        .build();
  }

  private static StreamingChatModel buildOllamaStreaming(
      final ChatModelConfig config, final ResponseFormat responseFormat) {
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
        .responseFormat(responseFormat)
        .timeout(DEFAULT_TIMEOUT)
        .build();
  }

  private static StreamingChatModel buildOpenAIStreaming(
      final ChatModelConfig config, final ResponseFormat responseFormat) {
    final String format = responseFormat.type() == ResponseFormatType.JSON ? "json" : null;
    return OpenAiStreamingChatModel.builder()
        .httpClientBuilder(LangchainUtils.httpClientBuilder())
        .modelName(config.getModel())
        .baseUrl(config.getBaseUrl())
        .apiKey(config.getApiKey())
        .temperature(config.getTemperature())
        .topP(config.getTopP())
        .stop(config.getStopTokens())
        .responseFormat(format)
        .returnThinking(config.isThoughtsEnabled())
        .timeout(DEFAULT_TIMEOUT)
        .build();
  }

  protected static ResponseFormat getResponseFormat(final ResponseFormatType responseFormatType) {
    return new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();
  }
}

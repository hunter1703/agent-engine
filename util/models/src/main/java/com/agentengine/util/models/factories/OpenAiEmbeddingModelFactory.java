package com.agentengine.util.models.factories;

import com.agentengine.util.models.config.EmbeddingModelConfig;
import com.agentengine.util.models.config.ModelConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.inject.Singleton;
import java.time.Duration;

@Singleton
public class OpenAiEmbeddingModelFactory implements EmbeddingModelFactory {

  private static final Duration DEFAULT_EMBEDDING_TIMEOUT = Duration.ofMinutes(2);

  @Override
  public String type() {
    return ModelConfig.Provider.OPEN_AI.name();
  }

  @Override
  public EmbeddingModel build(final EmbeddingModelConfig config) {
    return OpenAiEmbeddingModel.builder()
        .httpClientBuilder(LangchainUtils.httpClientBuilder())
        .baseUrl(config.getBaseUrl())
        .apiKey(config.getApiKey())
        .modelName(config.getModel())
        .timeout(DEFAULT_EMBEDDING_TIMEOUT)
        .build();
  }
}

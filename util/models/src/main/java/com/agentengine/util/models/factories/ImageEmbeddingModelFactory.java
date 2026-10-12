package com.agentengine.util.models.factories;

import com.agentengine.util.models.config.EmbeddingModelConfig;
import com.agentengine.util.models.config.ModelConfig;
import com.agentengine.util.models.embedding.ImageEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.inject.Singleton;
import java.time.Duration;

@Singleton
public class ImageEmbeddingModelFactory implements EmbeddingModelFactory {

  private static final Duration DEFAULT_EMBEDDING_TIMEOUT = Duration.ofSeconds(30);

  @Override
  public String type() {
    return ModelConfig.Provider.IMAGE_EMBEDDING.name();
  }

  @Override
  public EmbeddingModel build(final EmbeddingModelConfig config) {
    return ImageEmbeddingModel.builder()
        .baseUrl(config.getBaseUrl())
        .apiKey(config.getApiKey())
        .modelName(config.getModel())
        .dimensions(config.getDimensions())
        .timeout(DEFAULT_EMBEDDING_TIMEOUT)
        .build();
  }
}

package com.agentengine.util.models.factories;

import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.util.agents.beans.config.EmbeddingModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.RefCountedCache;
import com.agentengine.util.common.StringUtils;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and constructs an {@link EmbeddingModel} from a stored {@link EmbeddingModelConfig},
 * following the same config-driven pattern as the chat model factories.
 *
 * <p>Resolution order for the model ID:
 *
 * <ol>
 *   <li>The provided {@code modelId} if non-blank
 *   <li>{@link DefaultModelsRepository#getEmbeddingModelId()}
 * </ol>
 *
 * <p>The resolved ID is used to load an {@link EmbeddingModelConfig} from {@link ModelService},
 * which carries the provider, baseUrl, apiKey, and model name — identical to how chat models are
 * configured.
 */
@Singleton
public class EmbeddingModelFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingModelFactory.class);

  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

  private final DefaultModelsRepository defaultModelsRepository;
  private final RefCountedCache<String, Model> cache;

  @Inject
  public EmbeddingModelFactory(
      final ModelService modelService, final DefaultModelsRepository defaultModelsRepository) {
    this.defaultModelsRepository = defaultModelsRepository;
    this.cache =
        RefCountedCache.<String, Model>builder()
            .name("model-provider")
            .idleTimeout(15, TimeUnit.MINUTES)
            .cleanupInterval(60, TimeUnit.SECONDS)
            .creator(
                modelId -> {
                  final String resolvedId = resolveModelId(modelId);
                  final EmbeddingModelConfig config =
                      (EmbeddingModelConfig) modelService.getModel(resolvedId);
                  return build(config);
                })
            .onEvict(
                (key, model) ->
                    LOGGER.debug(
                        "Evicting embedding model : {} for key : {}",
                        model.embeddingModel().modelName(),
                        key))
            .build();
  }

  public EmbeddingModel get(final String modelId) {
    return cache.getAndAcquire(modelId).embeddingModel();
  }

  public void release(final String modelId) {
    cache.release(modelId);
  }

  public int getMaxEmbeddingBatchSize(final String modelId) {
    final EmbeddingModelConfig embeddingModelConfig = cache.getAndAcquire(modelId).modelConfig();
    return embeddingModelConfig.getMaxBatchSize();
  }

  private String resolveModelId(final String modelId) {
    if (StringUtils.isNotBlank(modelId)) {
      return modelId;
    }
    return defaultModelsRepository.getEmbeddingModelId();
  }

  private static Model build(final EmbeddingModelConfig config) {
    final ModelConfig.Provider provider = ModelConfig.Provider.fromType(config.getProvider());
    final String baseUrl = config.getBaseUrl();
    final String model = config.getModel();
    final EmbeddingModel embeddingModel =
        switch (provider) {
          case OLLAMA ->
              OllamaEmbeddingModel.builder()
                  .httpClientBuilder(LangchainUtils.httpClientBuilder())
                  .baseUrl(baseUrl)
                  .modelName(model)
                  .timeout(DEFAULT_TIMEOUT)
                  .build();
          case OPEN_AI_COMPATIBLE ->
              OpenAiEmbeddingModel.builder()
                  .httpClientBuilder(LangchainUtils.httpClientBuilder())
                  .baseUrl(baseUrl)
                  .apiKey(config.getApiKey())
                  .modelName(model)
                  .timeout(DEFAULT_TIMEOUT)
                  .build();
          default ->
              throw new IllegalArgumentException(
                  "Unsupported embedding model provider: " + provider);
        };
    return new Model(embeddingModel, config);
  }

  private record Model(EmbeddingModel embeddingModel, EmbeddingModelConfig modelConfig) {}
}

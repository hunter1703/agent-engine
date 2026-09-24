package com.agentengine.util.models.factories;

import com.agentengine.catalog.api.services.ModelCacheTag;
import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.util.agents.beans.config.DefaultModels;
import com.agentengine.util.agents.beans.config.EmbeddingModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.distributed.RefCountedDistributedCache;
import com.agentengine.util.models.factories.Model.LLMModel;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ModelProvider {
  private static final Logger LOG = LoggerFactory.getLogger(ModelProvider.class);
  private static final Duration DEFAULT_EMBEDDING_TIMEOUT = Duration.ofMinutes(2);

  private final Map<String, ModelFactory<?>> typeVsFactory;
  private final ModelFactory<?> defaultFactory;
  private final ModelService modelService;
  private final DefaultModelsRepository defaultModelsRepository;
  private final RefCountedDistributedCache<Model<?>> cache;

  @Inject
  public ModelProvider(
      final Instance<ModelFactory<?>> allFactories,
      final OpenAIModelFactory openAIModelFactory,
      final ModelService modelService,
      final DefaultModelsRepository defaultModelsRepository,
      final DistributedCacheManager cacheManager) {
    this.typeVsFactory =
        CollectionUtils.transformToMap(
            allFactories.stream().toList(), ModelFactory::type, Function.identity());
    this.defaultFactory = openAIModelFactory;
    this.modelService = modelService;
    this.defaultModelsRepository = defaultModelsRepository;
    final RefCountedDistributedCache.Builder<Model<?>> cacheBuilder =
        new RefCountedDistributedCache.Builder<Model<?>>("model-cache", cacheManager)
            .idleTimeout(15, TimeUnit.MINUTES)
            .cleanupInterval(60, TimeUnit.SECONDS)
            .onEvict(ModelProvider::tryClose)
            .tags(Set.of(ModelCacheTag.MODELS));
    this.cache = cacheBuilder.build();
  }

  public RefCounted<LLMModel> get(final String modelId) {
    final String resolvedId = resolveModelId(modelId, defaultModelsRepository::getChatModelId);
    return cache.getOrLoad(resolvedId, _ -> new LLMModel(buildChatModel(resolvedId)));
  }

  public RefCounted<Model.EmbeddingModel> getEmbeddingModel(final String modelId) {
    final String resolvedId = resolveModelId(modelId, defaultModelsRepository::getEmbeddingModelId);
    return cache.getOrLoad(
        resolvedId,
        _ -> {
          final EmbeddingModelConfig config = (EmbeddingModelConfig) modelService.getModel(modelId);
          return new Model.EmbeddingModel(buildEmbeddingModel(config), config.getMaxBatchSize());
        });
  }

  private static String resolveModelId(
      final String modelId, final Supplier<String> defaultModelId) {
    if (StringUtils.isNotBlank(modelId) && !DefaultModels.ID.equalsIgnoreCase(modelId)) {
      return modelId;
    }
    final String resolved = defaultModelId.get();
    if (StringUtils.isBlank(resolved)) {
      throw new IllegalStateException("Default model not configured for customer");
    }
    return resolved;
  }

  private BaseLlm buildChatModel(final String modelId) {
    final ModelConfig config = modelService.getModel(modelId);
    if (config == null) {
      throw new IllegalStateException("Model config missing for model_id=" + modelId);
    }
    final ModelFactory<?> factory =
        typeVsFactory.getOrDefault(config.getProvider(), defaultFactory);
    return factory.build(config);
  }

  private EmbeddingModel buildEmbeddingModel(final ModelConfig config) {
    final ModelConfig.Provider provider = ModelConfig.Provider.fromType(config.getProvider());
    final String baseUrl = config.getBaseUrl();
    final String model = config.getModel();
    return switch (provider) {
      case OLLAMA ->
          OllamaEmbeddingModel.builder()
              .httpClientBuilder(LangchainUtils.httpClientBuilder())
              .baseUrl(baseUrl)
              .modelName(model)
              .timeout(DEFAULT_EMBEDDING_TIMEOUT)
              .build();
      case OPEN_AI_COMPATIBLE ->
          OpenAiEmbeddingModel.builder()
              .httpClientBuilder(LangchainUtils.httpClientBuilder())
              .baseUrl(baseUrl)
              .apiKey(config.getApiKey())
              .modelName(model)
              .timeout(DEFAULT_EMBEDDING_TIMEOUT)
              .build();
      default ->
          throw new IllegalArgumentException("Unsupported embedding model provider: " + provider);
    };
  }

  private static void tryClose(final Model<?> model) {
    if (model instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ex) {
        LOG.debug("Failed to close model during cache eviction.", ex);
      }
    }
  }
}

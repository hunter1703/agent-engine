package com.agentengine.util.models.factories;

import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.distributed.RefCountedDistributedCache;
import com.agentengine.util.models.ConfiguredModel;
import com.agentengine.util.models.ModelLookup;
import com.agentengine.util.models.config.ChatModelConfig;
import com.agentengine.util.models.config.DefaultModels;
import com.agentengine.util.models.config.EmbeddingModelConfig;
import com.agentengine.util.models.config.ModelConfig;
import com.agentengine.util.models.embedding.ConfiguredEmbeddingModel;
import com.agentengine.util.models.repository.DefaultModelsRepository;
import com.google.adk.models.BaseLlm;
import com.google.genai.types.Modality;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ModelProvider {
  private static final Logger LOG = LoggerFactory.getLogger(ModelProvider.class);
  public static final String MODEL_CACHE = "model-cache";

  private final Map<String, ModelFactory<?>> typeVsFactory;
  private final Map<String, EmbeddingModelFactory> typeVsEmbeddingFactory;
  private final ModelLookup modelLookup;
  private final DefaultModelsRepository defaultModelsRepository;
  private final RefCountedDistributedCache<ConfiguredModel<?>> cache;

  @Inject
  public ModelProvider(
      final Instance<ModelFactory<?>> allFactories,
      final Instance<EmbeddingModelFactory> allEmbeddingFactories,
      final ModelLookup modelLookup,
      final DefaultModelsRepository defaultModelsRepository,
      final DistributedCacheManager cacheManager) {
    this.typeVsFactory =
        CollectionUtils.transformToMap(
            allFactories.stream().toList(), ModelFactory::type, Function.identity());
    this.typeVsEmbeddingFactory =
        CollectionUtils.transformToMap(
            allEmbeddingFactories.stream().toList(),
            EmbeddingModelFactory::type,
            Function.identity());
    this.modelLookup = modelLookup;
    this.defaultModelsRepository = defaultModelsRepository;
    final RefCountedDistributedCache.Builder<ConfiguredModel<?>> cacheBuilder =
        new RefCountedDistributedCache.Builder<ConfiguredModel<?>>(MODEL_CACHE, cacheManager)
            .idleTimeout(15, TimeUnit.MINUTES)
            .cleanupInterval(60, TimeUnit.SECONDS)
            .onEvict(ModelProvider::tryClose);
    this.cache = cacheBuilder.build();
  }

  /**
   * {@code modelId} may be a real model id, or one of the sentinels {@link DefaultModels#SMART_ID},
   * {@link DefaultModels#VISION_ID} or {@link DefaultModels#FAST_ID} — resolved here, on every
   * call, to the customer's configured default smart, vision or fast model respectively, since a
   * shared agent config (e.g. a community expert) can't hardcode a real, deployment-specific model
   * id, and a holder of the sentinel always gets the model that is the default now.
   */
  public RefCounted<ConfiguredModel<BaseLlm>> get(final String modelId) {
    final String resolvedId = resolveModelId(modelId, defaultModelIdSupplier(modelId));
    return cache
        .getOrLoad(
            resolvedId,
            id ->
                Context.require()
                    .asSystemCaller()
                    .get(() -> new ConfiguredModel<>(buildChatModel(id)) {}))
        .acquire();
  }

  /** The id of the model {@code modelId} names, which a default model sentinel resolves to. */
  public String resolveId(final String modelId) {
    return resolveModelId(modelId, defaultModelIdSupplier(modelId));
  }

  public boolean isToolCallingEnabled(final String modelId) {
    return modelConfig(modelId)
        .filter(ChatModelConfig.class::isInstance)
        .map(ChatModelConfig.class::cast)
        .map(ChatModelConfig::isToolCallingEnabled)
        .orElse(false);
  }

  /** The model's context length in tokens; empty when the model has none configured. */
  public OptionalInt getMaxContextLength(final String modelId) {
    return modelConfig(modelId)
        .filter(ChatModelConfig.class::isInstance)
        .map(ChatModelConfig.class::cast)
        .map(ChatModelConfig::getMaxContextLength)
        .map(OptionalInt::of)
        .orElse(OptionalInt.empty());
  }

  /** Whether the model takes images as input. */
  public boolean isVisionEnabled(final String modelId) {
    return modelConfig(modelId).map(config -> config.supports(Modality.Known.IMAGE)).orElse(false);
  }

  private Optional<ModelConfig> modelConfig(final String modelId) {
    return Context.require()
        .asSystemCaller()
        .get(
            () -> {
              final String resolvedId = resolveModelId(modelId, defaultModelIdSupplier(modelId));
              return StringUtils.isBlank(resolvedId)
                  ? Optional.empty()
                  : Optional.ofNullable(modelLookup.getModel(resolvedId));
            });
  }

  public RefCounted<ConfiguredEmbeddingModel> getDefaultEmbeddingModel(
      final Modality.Known modality) {
    final String modelId =
        switch (modality) {
          case TEXT -> DefaultModels.TEXT_EMBEDDING_ID;
          case IMAGE -> DefaultModels.IMAGE_EMBEDDING_ID;
          default ->
              throw new IllegalArgumentException(
                  "Unsupported modality for default embedding: " + modality);
        };
    return getEmbeddingModel(modelId);
  }

  /**
   * {@code modelId} may be a real model id, or one of the sentinels {@link
   * DefaultModels#TEXT_EMBEDDING_ID} / {@link DefaultModels#IMAGE_EMBEDDING_ID}.
   */
  public RefCounted<ConfiguredEmbeddingModel> getEmbeddingModel(final String modelId) {
    final String resolvedId = resolveEmbeddingModelId(modelId);
    return cache
        .getOrLoad(
            resolvedId, id -> Context.require().asSystemCaller().get(() -> loadEmbeddingModel(id)))
        .acquire();
  }

  /**
   * The real id of {@code modelId}, which may be one of the sentinels {@link
   * DefaultModels#TEXT_EMBEDDING_ID} / {@link DefaultModels#IMAGE_EMBEDDING_ID}, resolved against
   * the customer's defaults now.
   */
  public String resolveEmbeddingModelId(final String modelId) {
    if (StringUtils.isNotBlank(modelId) && isNotDefaultSentinel(modelId)) {
      return modelId;
    }
    final String resolved =
        DefaultModels.IMAGE_EMBEDDING_ID.equalsIgnoreCase(modelId)
            ? defaultModelsRepository.getEmbeddingModelId(Modality.Known.IMAGE)
            : defaultModelsRepository.getEmbeddingModelId(Modality.Known.TEXT);
    if (StringUtils.isBlank(resolved)) {
      throw new IllegalStateException("Default embedding model not configured for customer");
    }
    return resolved;
  }

  private Supplier<String> defaultModelIdSupplier(final String sentinel) {
    if (DefaultModels.VISION_ID.equalsIgnoreCase(sentinel)) {
      return defaultModelsRepository::getVisionModelId;
    }
    if (DefaultModels.FAST_ID.equalsIgnoreCase(sentinel)) {
      return defaultModelsRepository::getFastModelId;
    }
    return defaultModelsRepository::getSmartModelId;
  }

  private static String resolveModelId(
      final String modelId, final Supplier<String> defaultModelId) {
    if (StringUtils.isNotBlank(modelId) && isNotDefaultSentinel(modelId)) {
      return modelId;
    }
    final String resolved = defaultModelId.get();
    if (StringUtils.isBlank(resolved)) {
      throw new IllegalStateException("Default model not configured for customer");
    }
    return resolved;
  }

  private static boolean isNotDefaultSentinel(final String modelId) {
    return !DefaultModels.SMART_ID.equalsIgnoreCase(modelId)
        && !DefaultModels.VISION_ID.equalsIgnoreCase(modelId)
        && !DefaultModels.FAST_ID.equalsIgnoreCase(modelId)
        && !DefaultModels.TEXT_EMBEDDING_ID.equalsIgnoreCase(modelId)
        && !DefaultModels.IMAGE_EMBEDDING_ID.equalsIgnoreCase(modelId);
  }

  private BaseLlm buildChatModel(final String modelId) {
    final ModelConfig config = modelLookup.getModel(modelId);
    if (config == null) {
      throw new IllegalStateException("Model config missing for model_id=" + modelId);
    }
    final ModelFactory<?> factory = typeVsFactory.get(config.getProvider());
    if (factory == null) {
      throw new IllegalArgumentException("Unsupported model provider: " + config.getProvider());
    }
    return factory.build(config);
  }

  private ConfiguredEmbeddingModel loadEmbeddingModel(final String modelId) {
    final ModelConfig rawConfig = modelLookup.getModel(modelId);
    if (rawConfig == null) {
      throw new IllegalStateException("Embedding model config missing for model_id=" + modelId);
    }
    if (!(rawConfig instanceof EmbeddingModelConfig config)) {
      throw new IllegalStateException("Model is not an embedding model for model_id=" + modelId);
    }
    final EmbeddingModelFactory factory = typeVsEmbeddingFactory.get(config.getProvider());
    if (factory == null) {
      throw new IllegalArgumentException(
          "Unsupported embedding model provider: " + config.getProvider());
    }
    return new ConfiguredEmbeddingModel(modelId, factory.build(config), config.getMaxBatchSize());
  }

  private static void tryClose(final ConfiguredModel<?> model) {
    if (model instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ex) {
        LOG.debug("Failed to close model during cache eviction.", ex);
      }
    }
  }
}

package com.agentengine.util.models.repository;

import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.repository.EntityChangeListener;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.distributed.CacheEvictionListener;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.models.config.DefaultModels;
import com.google.genai.types.Modality;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.function.Function;

@Singleton
@Startup
public class DefaultModelsRepository extends AbstractRepository<DefaultModels> {

  private static final String CACHE_NAME = "DEFAULT_MODELS_CACHE";

  private final DistributedCache<DefaultModels> cache;

  @Inject
  public DefaultModelsRepository(
      final DocumentBackend documentBackend,
      final ValidationService validationService,
      final DistributedCacheManager cacheManager) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                ModelDocumentStoreClientType.AGENT, DefaultModels.class)),
        validationService);
    this.cache =
        new DistributedCache.Builder<DefaultModels>(CACHE_NAME, cacheManager)
            .loader(this::findById)
            .build();
  }

  public String getSmartModelId() {
    return get(DefaultModels::getSmartModelId);
  }

  public String getVisionModelId() {
    return get(DefaultModels::getVisionModelId);
  }

  public String getFastModelId() {
    return get(DefaultModels::getFastModelId);
  }

  public String getEmbeddingModelId(final Modality.Known modality) {
    return get(defaultModels -> defaultModels.getEmbeddingModelId(modality));
  }

  /** Evicts the default models from this repository's cache, on every node, when written. */
  @Produces
  @Singleton
  public static EntityChangeListener<DefaultModels> evictionListener(
      final DistributedCacheManager cacheManager) {
    return new CacheEvictionListener<>(DefaultModels.class, CACHE_NAME, cacheManager);
  }

  private String get(final Function<DefaultModels, String> getModelId) {
    return Optional.ofNullable(cache.get(DefaultModels.ID)).map(getModelId).orElse(null);
  }
}

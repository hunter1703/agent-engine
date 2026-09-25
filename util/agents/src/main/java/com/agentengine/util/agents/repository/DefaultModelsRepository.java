package com.agentengine.util.agents.repository;

import com.agentengine.util.agents.beans.config.DefaultModels;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public class DefaultModelsRepository extends AbstractMongoRepository<DefaultModels> {

  private static final String CACHE_NAME = "DEFAULT_MODELS_CACHE";

  private final DistributedCache<DefaultModels> cache;

  @Inject
  public DefaultModelsRepository(
      final MongoClientFactory mongoClientFactory,
      final ValidationService validationService,
      final DistributedCacheManager cacheManager) {
    super(
        mongoClientFactory,
        AgentMongoStoreClientType.AGENT,
        DefaultModels.class,
        validationService);
    this.cache =
        new DistributedCache.Builder<DefaultModels>(CACHE_NAME, cacheManager)
            .loader(this::findById)
            .build();
  }

  @Override
  public DefaultModels save(final DefaultModels defaultModels) {
    final DefaultModels saved = super.save(defaultModels);
    cache.invalidate(DefaultModels.ID);
    return saved;
  }

  public String getFastModelId() {
    return get(DefaultModels::getFastModelId);
  }

  public String getCompactionModelId() {
    return get(DefaultModels::getCompactionModelId);
  }

  public String getEvaluatorModelId() {
    return get(DefaultModels::getEvaluatorModelId);
  }

  public String getEmbeddingModelId() {
    return get(DefaultModels::getEmbeddingModelId);
  }

  public String getChatModelId() {
    return get(DefaultModels::getChatModelId);
  }

  public String getVisionModelId() {
    return get(DefaultModels::getVisionModelId);
  }

  private String get(final Function<DefaultModels, String> getModelId) {
    return Optional.ofNullable(cache.get(DefaultModels.ID)).map(getModelId).orElse(null);
  }
}

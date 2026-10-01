package com.agentengine.util.mongodb.infra;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraCacheTag;
import com.agentengine.util.infra.InfraConfig;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.agentengine.util.mongodb.mongo.MongoUtils;
import com.google.common.cache.CacheBuilder;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class InfraConfigServiceImpl implements InfraConfigService {

  private static final String CACHE_NAME = "INFRA_CONFIG_CACHE";
  private static final String DATABASE = "INFRA";
  private static final String COLLECTION = "InfraConfig";
  private static final int DUPLICATE_KEY_ERROR = 11000;
  private static final String FIELD_TYPE = "type";

  private final LazyLoader<MongoClientFactory> mongoClientFactory;
  private final DistributedCacheManager cacheManager;
  private final DistributedCache<InfraConfig> cache;

  @Inject
  public InfraConfigServiceImpl(
      final Provider<MongoClientFactory> mongoClientFactory,
      final DistributedCacheManager cacheManager) {
    this.mongoClientFactory = new LazyLoader<>(mongoClientFactory::get);
    this.cacheManager = cacheManager;
    this.cache =
        new DistributedCache.Builder<InfraConfig>(CACHE_NAME, cacheManager)
            .scope(CacheScope.GLOBAL)
            .localCache(CacheBuilder.newBuilder().maximumSize(2048))
            .build();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends InfraConfig> T get(final String id) {
    return (T) cache.get(id, this::load);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends InfraConfig> List<T> findServers(final ServerType serverType) {
    return (List<T>)
        collection().find(Filters.eq(FIELD_TYPE, serverType.name())).into(new ArrayList<>());
  }

  @Override
  public <T extends InfraConfig> T save(final T config) {
    final long now = System.currentTimeMillis();
    final InfraConfig existing = load(config.getId());
    config.setCreatedTime(existing == null ? now : existing.getCreatedTime());
    config.setUpdatedTime(now);
    config.setVersion(existing == null ? 1 : existing.getVersion() + 1);
    // Provisioning sets a config to a given state, so the caller's version is not checked; the
    // write is still guarded against one landing between the read above and this one.
    final UpdateResult result =
        collection()
            .replaceOne(
                existing == null
                    ? Filters.eq(MongoUtils.FIELD_MONGO_ID, config.getId())
                    : Filters.and(
                        Filters.eq(MongoUtils.FIELD_MONGO_ID, config.getId()),
                        Filters.eq(BaseEntity.FIELD_VERSION, existing.getVersion())),
                config,
                new ReplaceOptions().upsert(existing == null));
    if (result.getMatchedCount() == 0 && result.getUpsertedId() == null) {
      throw new StaleStateException(config.getId(), existing == null ? 0L : existing.getVersion());
    }
    cache.invalidate(config.getId());
    cacheManager.invalidate(InfraCacheTag.INFRA_CONNECTION.name(), config.getId());
    return config;
  }

  @Override
  public void insert(final InfraConfig config) {
    final long now = System.currentTimeMillis();
    config.setCreatedTime(now);
    config.setUpdatedTime(now);
    config.setVersion(1);
    try {
      collection().insertOne(config);
      cache.invalidate(config.getId());
    } catch (final MongoWriteException exception) {
      if (exception.getError().getCode() != DUPLICATE_KEY_ERROR) {
        throw exception;
      }
      cache.invalidate(config.getId());
      throw new DuplicateAssetException(config.getType(), config.getId());
    }
  }

  private InfraConfig load(final String id) {
    return collection().find(Filters.eq(MongoUtils.FIELD_MONGO_ID, id)).first();
  }

  private MongoCollection<InfraConfig> collection() {
    return mongoClientFactory
        .get()
        .getInfraClient()
        .getDatabase(DATABASE)
        .getCollection(COLLECTION, InfraConfig.class);
  }
}

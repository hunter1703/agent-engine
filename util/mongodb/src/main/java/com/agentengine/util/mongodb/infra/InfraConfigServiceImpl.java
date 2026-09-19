package com.agentengine.util.mongodb.infra;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraCacheTag;
import com.agentengine.util.infra.InfraConfig;
import com.agentengine.util.infra.InfraConfigService;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class InfraConfigServiceImpl implements InfraConfigService {

  private static final String CACHE_NAME = "INFRA_CONFIG_CACHE";
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final InfraMongoRepository repository;
  private final DistributedCacheManager cacheManager;
  private final DistributedCache<InfraConfig> cache;

  @Inject
  public InfraConfigServiceImpl(
      final InfraMongoRepository repository, final DistributedCacheManager cacheManager) {
    this.repository = repository;
    this.cacheManager = cacheManager;
    this.cache =
        new DistributedCache<>(
            CACHE_NAME,
            Set.of(InfraCacheTag.INFRA_CONFIG),
            CacheBuilder.newBuilder().maximumSize(256).expireAfterWrite(CACHE_TTL),
            repository::findById,
            cacheManager);
    this.cache.init();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends InfraConfig> T findById(final String id) {
    return (T) cache.get(id);
  }

  @Override
  public List<InfraConfig> saveAll(final List<InfraConfig> configs) {
    for (final InfraConfig config : configs) {
      if (StringUtils.isBlank(config.getId())) {
        throw new IllegalArgumentException("Infra config has no id");
      }
    }
    final List<InfraConfig> saved = new ArrayList<>();
    for (final InfraConfig config : configs) {
      final InfraConfig existing = repository.findById(config.getId());
      if (existing != null) {
        config.setCreatedTime(existing.getCreatedTime());
        config.setVersion(existing.getVersion());
      }
      saved.add(repository.save(config));
      cacheManager.invalidateAll(InfraCacheTag.INFRA_CONFIG);
    }
    return saved;
  }
}

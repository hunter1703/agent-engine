package com.agentengine.util.infra;

import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class InfraClientFactory<
    C extends InfraClientConfig, S extends InfraConfig, T extends AutoCloseable> {

  private static final Logger LOG = LoggerFactory.getLogger(InfraClientFactory.class);

  private final DistributedCache<T> clients;

  protected InfraClientFactory(
      final InfraConfigService infraConfigService,
      final DistributedCacheManager cacheManager,
      final String clientType) {
    this.clients =
        new DistributedCache<>(
            "INFRA_CLIENT_CACHE_" + clientType,
            Set.of(InfraCacheTag.INFRA_CONFIG),
            CacheBuilder.newBuilder(),
            clientId -> {
              final C clientConfig = infraConfigService.findById(clientType, clientId);
              if (clientConfig == null) {
                throw new IllegalStateException(
                    "No config '%s:%s'".formatted(clientType, clientId));
              }
              final S serverConfig = infraConfigService.findServer(clientConfig);
              return create(clientConfig, serverConfig);
            },
            InfraClientFactory::close,
            cacheManager);
    this.clients.init();
  }

  public T get(final String clientId) {
    return clients.get(clientId);
  }

  @PreDestroy
  public void closeAll() {
    clients.invalidateAll(true);
  }

  protected abstract T create(C clientConfig, S serverConfig);

  private static void close(final AutoCloseable client) {
    try {
      client.close();
    } catch (final Exception exception) {
      LOG.warn("Failed to close an evicted infra client", exception);
    }
  }
}

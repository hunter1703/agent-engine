package com.agentengine.util.infra;

import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class InfraClientFactory<
    C extends InfraConfig, S extends InfraConfig, T extends AutoCloseable> {

  private static final Logger LOG = LoggerFactory.getLogger(InfraClientFactory.class);

  protected final InfraConfigService infraConfigService;
  private final ServerType serverType;
  private final DistributedCache<T> connections;

  protected InfraClientFactory() {
    this.infraConfigService = null;
    this.serverType = null;
    this.connections = null;
  }

  protected InfraClientFactory(
      final InfraConfigService infraConfigService,
      final DistributedCacheManager cacheManager,
      final ServerType serverType) {
    this.infraConfigService = infraConfigService;
    this.serverType = serverType;
    this.connections =
        DistributedCache.<T>builder("INFRA_CONNECTION_CACHE_" + serverType, cacheManager)
            .scope(CacheScope.GLOBAL)
            .tags(Set.of(InfraCacheTag.INFRA_CONNECTION))
            .removalListener(InfraClientFactory::close)
            .build();
  }

  public T get(final C clientConfig) {
    if (clientConfig == null) {
      return null;
    }
    final S serverConfig = infraConfigService.getServer(serverType, clientConfig);
    if (serverConfig == null) {
      return null;
    }
    return getClientForServer(serverConfig);
  }

  @PreDestroy
  public void closeAll() {
    connections.invalidateAll(true);
  }

  protected final C getOrCreate(final String clientId, final Supplier<C> defaultClient) {
    final C existing = infraConfigService.get(clientId);
    if (existing != null) {
      return existing;
    }
    final C created = defaultClient.get();
    try {
      infraConfigService.insert(created);
      return created;
    } catch (final DuplicateAssetException exception) {
      // Another node made it first; theirs is the one to use.
      return infraConfigService.get(clientId);
    }
  }

  protected T getClientForServer(S serverConfig) {
    return connections.get(serverConfig.getId(), _ -> create(serverConfig));
  }

  protected abstract T create(S serverConfig);

  private static void close(final AutoCloseable connection) {
    try {
      connection.close();
    } catch (final Exception exception) {
      LOG.warn("Failed to close an evicted infra connection", exception);
    }
  }
}

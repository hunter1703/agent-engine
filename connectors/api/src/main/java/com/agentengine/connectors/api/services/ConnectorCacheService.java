package com.agentengine.connectors.api.services;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
public class ConnectorCacheService {
  public static final String CONNECTION_IDS_CACHE_NAME = "CONNECTION_IDS_CACHE";
  public static final String CONNECTOR_METADATA_CACHE_NAME = "CONNECTOR_METADATA_CACHE";

  private final DistributedCache<List<String>> connectionsCache;
  private final DistributedCache<ConnectorMetadata> connectorMetadataCache;

  @Inject
  public ConnectorCacheService(
      ConnectionService connectionService, DistributedCacheManager cacheManager) {
    this.connectionsCache =
        new DistributedCache.Builder<List<String>>(CONNECTION_IDS_CACHE_NAME, cacheManager)
            .tags(Set.of(ConnectionCacheTag.CONNECTIONS))
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .loader(
                appName -> {
                  final List<Connection> result =
                      connectionService.getConnections(appName, new Page(0, 100)).getItems();
                  return CollectionUtils.nullSafeList(result).stream()
                      .map(Connection::getId)
                      .toList();
                })
            .build();

    this.connectorMetadataCache =
        new DistributedCache.Builder<ConnectorMetadata>(CONNECTOR_METADATA_CACHE_NAME, cacheManager)
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .loader(
                key -> {
                  String[] parts = key.split(":");
                  if (parts.length == 2) {
                    return connectionService.getConnectorMetadata(parts[0], parts[1]);
                  }
                  return null;
                })
            .build();
  }

  public List<String> getConnectionsForApp(String appName) {
    return connectionsCache.get(appName);
  }

  public ConnectorMetadata getConnectorMetadata(String appName, String connectorName) {
    return connectorMetadataCache.get(appName + ":" + connectorName);
  }
}

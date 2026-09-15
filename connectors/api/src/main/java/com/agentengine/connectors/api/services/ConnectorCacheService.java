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
import java.util.concurrent.TimeUnit;

@Singleton
public class ConnectorCacheService {
  public static final String CONNECTION_IDS_CACHE_NAME = "CONNECTION_IDS_CACHE";
  public static final String CONNECTOR_METADATA_CACHE_NAME = "CONNECTOR_METADATA_CACHE";

  private final DistributedCache<List<String>> connectionsCache;
  private final DistributedCache<ConnectorMetadata> connectorMetadataCache;

  @Inject
  public ConnectorCacheService(
      ConnectionService connectionService,
      ConnectorService connectorService,
      DistributedCacheManager cacheManager) {
    this.connectionsCache =
        new DistributedCache<>(
            CONNECTION_IDS_CACHE_NAME,
            CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS),
            appName -> {
              final List<Connection> result =
                  connectionService.getConnections(appName, new Page(0, 100)).getItems();
              return CollectionUtils.nullSafeList(result).stream().map(Connection::getId).toList();
            },
            cacheManager);

    this.connectorMetadataCache =
        new DistributedCache<>(
            CONNECTOR_METADATA_CACHE_NAME,
            CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS),
            key -> {
              String[] parts = key.split(":");
              if (parts.length == 2) {
                return connectorService.describe(parts[0], parts[1]);
              }
              return null;
            },
            cacheManager);
  }

  public List<String> getConnectionsForApp(String appName) {
    return connectionsCache.get(appName);
  }

  public ConnectorMetadata getConnectorMetadata(String appName, String connectorName) {
    return connectorMetadataCache.get(appName + ":" + connectorName);
  }
}

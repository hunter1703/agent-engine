package com.agentengine.connectors.api.services;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionChecker;
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

  private final PermissionChecker permissionChecker;
  private final DistributedCache<List<Connection>> connectionsCache;
  private final DistributedCache<ConnectorMetadata> connectorMetadataCache;

  @Inject
  public ConnectorCacheService(
      final ConnectionService connectionService,
      final PermissionChecker permissionChecker,
      final DistributedCacheManager cacheManager) {
    this.permissionChecker = permissionChecker;
    this.connectionsCache =
        new DistributedCache.Builder<List<Connection>>(CONNECTION_IDS_CACHE_NAME, cacheManager)
            .tags(Set.of(ConnectionCacheTag.CONNECTIONS))
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .loader(
                appName ->
                    Context.require().asSystemCaller().get(() -> load(connectionService, appName)))
            .build();

    this.connectorMetadataCache =
        new DistributedCache.Builder<ConnectorMetadata>(CONNECTOR_METADATA_CACHE_NAME, cacheManager)
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .loader(
                key -> {
                  String[] parts = key.split(ID_SEPARATOR);
                  if (parts.length == 2) {
                    return connectionService.getConnectorMetadata(parts[0], parts[1]);
                  }
                  return null;
                })
            .build();
  }

  public List<String> getConnectionsForApp(final String appName) {
    return CollectionUtils.nullSafeList(connectionsCache.get(appName)).stream()
        .filter(
            connection ->
                permissionChecker.hasPermission(
                    () -> connection, AssetClass.CONNECTION, Permission.READ))
        .map(Connection::getId)
        .toList();
  }

  public ConnectorMetadata getConnectorMetadata(String appName, String connectorName) {
    return connectorMetadataCache.get(appName + ID_SEPARATOR + connectorName);
  }

  /** The app's connections, each holding only what a permission check on it reads. */
  private static List<Connection> load(
      final ConnectionService connectionService, final String appName) {
    return CollectionUtils.nullSafeList(
        connectionService
            .getConnections(
                appName, Page.UNBOUNDED, List.of(BaseEntity.FIELD_ID, BaseEntity.FIELD_ACL), null)
            .getItems());
  }
}

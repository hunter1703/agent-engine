package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.util.common.repository.EntityChange;
import com.agentengine.util.common.repository.EntityChangeListener;
import com.agentengine.util.distributed.DistributedCacheManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Drops the customer's cached connections of every app, on every node, whenever a connection is
 * written or its access list changes: the cache keeps each connection's access list, and a write
 * names no app when only access changed.
 */
@Singleton
public class ConnectionsEvictionListener implements EntityChangeListener<Connection> {

  private final DistributedCacheManager cacheManager;

  @Inject
  public ConnectionsEvictionListener(final DistributedCacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  @Override
  public Class<Connection> entityClass() {
    return Connection.class;
  }

  @Override
  public void onChange(final EntityChange<Connection> change) {
    cacheManager.invalidateInCustomerScope(ConnectorCacheService.CONNECTION_IDS_CACHE_NAME);

    if (change instanceof EntityChange.Entities<Connection> entities) {
      entities
          .idVsEntity()
          .keySet()
          .forEach(id -> cacheManager.invalidate(ConnectionServiceImpl.CACHE_NAME, id));
    } else if (change instanceof EntityChange.Ids<Connection> ids) {
      ids.ids().forEach(id -> cacheManager.invalidate(ConnectionServiceImpl.CACHE_NAME, id));
    }
  }
}

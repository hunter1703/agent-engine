package com.agentengine.util.distributed;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.repository.EntityChange;
import com.agentengine.util.common.repository.EntityChangeListener;

/**
 * Evicts the entries of a cache keyed by entity id, on every node, as those entities change. A
 * write by a filter evicts the current customer's entries, since which of them it changed is
 * unknown.
 */
public final class CacheEvictionListener<T extends BaseEntity> implements EntityChangeListener<T> {

  private final Class<T> entityClass;
  private final String cacheName;
  private final DistributedCacheManager cacheManager;

  public CacheEvictionListener(
      final Class<T> entityClass,
      final String cacheName,
      final DistributedCacheManager cacheManager) {
    this.entityClass = entityClass;
    this.cacheName = cacheName;
    this.cacheManager = cacheManager;
  }

  @Override
  public Class<T> entityClass() {
    return entityClass;
  }

  @Override
  public void onChange(final EntityChange<T> change) {
    switch (change) {
      case EntityChange.Entities<T> changed ->
          changed.idVsEntity().keySet().forEach(id -> cacheManager.invalidate(cacheName, id));
      case EntityChange.Ids<T> changed ->
          changed.ids().forEach(id -> cacheManager.invalidate(cacheName, id));
    }
  }
}

package com.agentengine.util.tenancy;

import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.context.Context;
import com.agentengine.util.distributed.CacheEvictionListener;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import java.util.Objects;
import java.util.function.Function;

/**
 * A customer's {@link Permissioned} entities by id, each loaded once for the whole customer — as
 * the customer's system, whoever reads it first — and checked against the reader's permissions on
 * every read. An id with no entity is cached as such. A {@link CacheEvictionListener} listener on
 * the entities' writes evicts them. A read returns a copy of the cached entity, so a reader that
 * changes it changes no one else's.
 */
public final class PermissionedCache<T extends BaseEntity> {

  private final String assetClass;
  private final DistributedCache<T> cache;
  private final PermissionChecker permissionChecker;

  private PermissionedCache(final Builder<T> builder) {
    this.assetClass = assetClassOf(builder.entityClass);
    this.permissionChecker = builder.permissionChecker;
    final Function<String, ? extends T> loader = Objects.requireNonNull(builder.loader, "loader");
    this.cache =
        new DistributedCache.Builder<T>(builder.cacheName, builder.cacheManager)
            .scope(CacheScope.CUSTOMER)
            .localCache(builder.localCache)
            .loader(id -> Context.require().asSystemCaller().get(() -> loader.apply(id)))
            .build();
  }

  /** The entity, when it exists and the caller may read it; null otherwise. */
  public T get(final String id) {
    return get(id, Permission.READ);
  }

  /** The entity, when it exists and the caller holds {@code permission} on it; null otherwise. */
  public T get(final String id, final Permission permission) {
    final T entity = cache.get(id);
    return entity != null && permissionChecker.hasPermission(() -> entity, assetClass, permission)
        ? JsonUtils.copy(entity)
        : null;
  }

  private static String assetClassOf(final Class<? extends BaseEntity> entityClass) {
    final Permissioned permissioned = entityClass.getAnnotation(Permissioned.class);
    if (permissioned == null) {
      throw new ConfigurationException(entityClass.getSimpleName() + " is not @Permissioned");
    }
    return permissioned.assetClass();
  }

  public static final class Builder<T extends BaseEntity> {
    private final String cacheName;
    private final Class<T> entityClass;
    private final DistributedCacheManager cacheManager;
    private final PermissionChecker permissionChecker;
    private CacheBuilder<Object, Object> localCache = CacheBuilder.newBuilder();
    private Function<String, ? extends T> loader;

    public Builder(
        final String cacheName,
        final Class<T> entityClass,
        final DistributedCacheManager cacheManager,
        final PermissionChecker permissionChecker) {
      this.cacheName = cacheName;
      this.entityClass = entityClass;
      this.cacheManager = cacheManager;
      this.permissionChecker = permissionChecker;
    }

    public Builder<T> localCache(final CacheBuilder<Object, Object> localCache) {
      this.localCache = localCache;
      return this;
    }

    /** Reads an entity by id, or null when there is none; run as the customer's system. */
    public Builder<T> loader(final Function<String, ? extends T> loader) {
      this.loader = loader;
      return this;
    }

    public PermissionedCache<T> build() {
      return new PermissionedCache<>(this);
    }
  }
}

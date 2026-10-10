package com.agentengine.tenancy;

import com.agentengine.tenancy.beans.User;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.repository.EntityChangeListener;
import com.agentengine.util.context.Context;
import com.agentengine.util.distributed.CacheEvictionListener;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A customer's users by id, for the services that read them on every request. Only {@link
 * #CACHED_FIELDS} of a user are held, loaded once for the whole customer, as the customer's system,
 * whoever reads it first. A read is not checked against the reader's permissions, so a field is
 * held only if every service may know it. An entry expires after ten minutes, and is evicted on
 * every node when the user is written.
 */
@Singleton
public class UserCache {

  private static final String USER_CACHE = "USER_CACHE";
  private static final long MAX_SIZE = 50_000;
  private static final long TTL_MINUTES = 10;

  /** What the cache holds of a user, besides its id. */
  private static final List<String> CACHED_FIELDS = List.of(User.FIELD_TIME_ZONE);

  private final DistributedCache<User> cache;

  @Inject
  public UserCache(final UserService userService, final DistributedCacheManager cacheManager) {
    this.cache =
        new DistributedCache.Builder<User>(USER_CACHE, cacheManager)
            .scope(CacheScope.CUSTOMER)
            .localCache(
                CacheBuilder.newBuilder()
                    .maximumSize(MAX_SIZE)
                    .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES))
            .loader(
                userId ->
                    Context.require()
                        .asSystemCaller()
                        .get(() -> userService.get(userId, CACHED_FIELDS)))
            .build();
  }

  /** The user with only its cached fields loaded, or null when there is none. */
  public User get(final String userId) {
    final User user = cache.get(userId);
    return user == null ? null : JsonUtils.copy(user);
  }

  /** Evicts a user from the cache, on every node, whenever it is written. */
  @Produces
  @Singleton
  public static EntityChangeListener<User> evictionListener(
      final DistributedCacheManager cacheManager) {
    return new CacheEvictionListener<>(User.class, USER_CACHE, cacheManager);
  }
}

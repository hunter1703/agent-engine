package com.agentengine.catalog.api.services;

import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class SessionCacheService {

  public static final String SESSION_CACHE_NAME = "SESSION_CACHE";

  private static final List<String> LOADED_FIELDS = List.of(BaseEntity.FIELD_OWNER_USER_ID);

  private final DistributedCache<AgentSession> cache;

  @Inject
  public SessionCacheService(
      final SessionService sessionService, final DistributedCacheManager cacheManager) {
    this.cache =
        new DistributedCache.Builder<AgentSession>(SESSION_CACHE_NAME, cacheManager)
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .loader(sessionId -> sessionService.getSession(sessionId, LOADED_FIELDS))
            .build();
  }

  public AgentSession getSession(final String sessionId) {
    return cache.get(sessionId);
  }
}

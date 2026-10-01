package com.agentengine.catalog.api.services;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.repository.EntityChangeListener;
import com.agentengine.util.distributed.CacheEvictionListener;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.PermissionedCache;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The catalog's agents and sessions, cached for the services that read them on every request. */
@Singleton
public class CatalogCaches {

  private static final String AGENT_CACHE = "AGENT_CACHE";
  private static final String SESSION_CACHE = "SESSION_CACHE";
  private static final long AGENT_CACHE_MAX_SIZE = 10_000;
  private static final long SESSION_CACHE_MAX_SIZE = 50_000;
  private static final long TTL_MINUTES = 10;

  /** The fields of a session that never change once it exists. */
  private static final List<String> FIXED_SESSION_FIELDS =
      List.of(
          BaseEntity.FIELD_CREATED_BY,
          AgentSession.FIELD_AGENT_ID,
          AgentSession.FIELD_PARENT_SESSION_ID,
          AgentSession.FIELD_ROOT_SESSION_ID,
          AgentSession.FIELD_ROOT_AGENT_ID,
          AgentSession.FIELD_DEPTH);

  @Produces
  @Singleton
  public static PermissionedCache<BaseAgentConfig> agentCache(
      final AgentService agentService,
      final DistributedCacheManager cacheManager,
      final PermissionChecker permissionChecker) {
    return new PermissionedCache.Builder<>(
            AGENT_CACHE, BaseAgentConfig.class, cacheManager, permissionChecker)
        .localCache(
            CacheBuilder.newBuilder()
                .maximumSize(AGENT_CACHE_MAX_SIZE)
                .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES))
        .loader(agentService::getAgent)
        .build();
  }

  /** Sessions with only their fixed fields loaded; a changing one, such as status, is not. */
  @Produces
  @Singleton
  public static PermissionedCache<AgentSession> sessionCache(
      final SessionService sessionService,
      final DistributedCacheManager cacheManager,
      final PermissionChecker permissionChecker) {
    return new PermissionedCache.Builder<>(
            SESSION_CACHE, AgentSession.class, cacheManager, permissionChecker)
        .localCache(
            CacheBuilder.newBuilder()
                .maximumSize(SESSION_CACHE_MAX_SIZE)
                .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES))
        .loader(sessionId -> sessionService.getSession(sessionId, FIXED_SESSION_FIELDS))
        .build();
  }

  /** Evicts an agent from its cache, on every node, whenever it is written. */
  @Produces
  @Singleton
  public static EntityChangeListener<BaseAgentConfig> agentEvictionListener(
      final DistributedCacheManager cacheManager) {
    return new CacheEvictionListener<>(BaseAgentConfig.class, AGENT_CACHE, cacheManager);
  }

  /** Evicts a session from its cache, on every node, whenever it is written. */
  @Produces
  @Singleton
  public static EntityChangeListener<AgentSession> sessionEvictionListener(
      final DistributedCacheManager cacheManager) {
    return new CacheEvictionListener<>(AgentSession.class, SESSION_CACHE, cacheManager);
  }
}

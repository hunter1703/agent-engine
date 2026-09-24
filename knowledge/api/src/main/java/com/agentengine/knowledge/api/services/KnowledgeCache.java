package com.agentengine.knowledge.api.services;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.util.common.Cache;
import com.agentengine.util.common.StringUtils;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class KnowledgeCache {

  private final Cache<String, Knowledge> cache;

  @Inject
  public KnowledgeCache(final KnowledgeService knowledgeService) {
    this.cache =
        new Cache<>(
            CacheBuilder.newBuilder().maximumSize(5000).expireAfterWrite(10, TimeUnit.MINUTES),
            knowledgeService::findById);
  }

  public Knowledge get(final String knowledgeId) {
    return StringUtils.isBlank(knowledgeId) ? null : cache.get(knowledgeId);
  }
}

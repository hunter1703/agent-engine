package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetHandler;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class SessionAssetHandler implements AssetHandler<AgentSession> {

  private static final String ASSET_TYPE = "session";

  private final AgentSessionRepository agentSessionRepository;

  @Inject
  public SessionAssetHandler(AgentSessionRepository agentSessionRepository) {
    this.agentSessionRepository = agentSessionRepository;
  }

  @Override
  public String getAssetType() {
    return ASSET_TYPE;
  }

  @Override
  public PaginatedResult<AgentSession> findAssets(AssetRequest request) {
    return agentSessionRepository.findByQuery(request.getQuery());
  }

  @Override
  public Map<String, AgentSession> getAssetsByIds(AssetRequest request) {
    Map<String, AgentSession> result = new HashMap<>();
    if (request.getKeys() == null || request.getKeys().isEmpty()) {
      return result;
    }

    for (String key : request.getKeys()) {
      agentSessionRepository.findById(key).ifPresent(value -> result.put(key, value));
    }

    return result;
  }
}
package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.catalog.NamedAssetHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class AgentAssetHandler extends NamedAssetHandler<AgentConfig> {

  private static final String ASSET_TYPE = "agent";

  private final AgentService agentService;

  @Inject
  public AgentAssetHandler(AgentService agentService) {
    this.agentService = agentService;
  }

  @Override
  public String getAssetType() {
    return ASSET_TYPE;
  }

  @Override
  public PaginatedResult<AgentConfig> findAssets(AssetRequest request) {
    return agentService.findAgents(request.getQuery());
  }

  @Override
  public Map<String, AgentConfig> getAssetsByIds(AssetRequest request) {
    Map<String, AgentConfig> result = new HashMap<>();
    if (request.getKeys() == null || request.getKeys().isEmpty()) {
      return result;
    }

    for (String key : request.getKeys()) {
      agentService.getAgent(key).ifPresent(value -> result.put(key, value));
    }

    return result;
  }

  @Override
  protected String getName(AgentConfig asset) {
    return asset.getName();
  }
}
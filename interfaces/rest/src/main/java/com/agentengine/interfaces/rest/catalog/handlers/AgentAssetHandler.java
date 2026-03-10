package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.catalog.NamedAssetHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class AgentAssetHandler extends NamedAssetHandler<BaseAgentConfig> {

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
  public PaginatedResult<BaseAgentConfig> findAssets(AssetRequest request) {
    return agentService.findAgents(request.getQuery());
  }

  @Override
  public Map<String, BaseAgentConfig> getAssetsByIds(AssetRequest request) {
      return agentService.getAgents(request.getKeys());
  }
}

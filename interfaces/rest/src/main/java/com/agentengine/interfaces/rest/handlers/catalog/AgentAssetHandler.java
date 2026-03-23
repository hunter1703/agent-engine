package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.core.api.services.AgentService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.common.query.PaginatedResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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

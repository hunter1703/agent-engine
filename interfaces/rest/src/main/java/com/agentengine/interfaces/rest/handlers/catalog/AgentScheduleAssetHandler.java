package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.agents.beans.AgentSchedule;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class AgentScheduleAssetHandler implements AssetHandler<AgentSchedule> {

  private final RuntimeService runtimeService;

  @Inject
  public AgentScheduleAssetHandler(final MicroServiceClientProvider provider) {
    this.runtimeService = provider.getRaw(RuntimeService.class);
  }

  @Override
  public String getAssetType() {
    return AssetClass.AGENT_SCHEDULE;
  }

  @Override
  public PaginatedResult<AgentSchedule> findAssets(final AssetRequest request) {
    return runtimeService.findSchedules(
        request.getQuery() == null ? new Query() : new Query(request.getQuery()));
  }

  @Override
  public Map<String, AgentSchedule> getAssetsByIds(final AssetRequest request) {
    return runtimeService.getSchedules(CollectionUtils.nullSafeList(request.getKeys()));
  }
}

package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class AgentAssetHandler extends NamedAssetHandler<BaseAgentConfig> {

    private final AgentService agentService;

    @Inject
    public AgentAssetHandler(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public String getAssetType() {
        return AssetClass.AGENT;
    }

    @Override
    public PaginatedResult<BaseAgentConfig> findAssets(AssetRequest request) {
        return agentService.findAgents(request.getQuery());
    }

    @Override
    public Map<String, BaseAgentConfig> getAssetsByIds(AssetRequest request) {
        return agentService.getAgents(request.getKeys());
    }

    @Override
    public PaginatedResult<BaseAgentConfig> listAssets(final AssetRequest request) {
        Query query = request.getQuery();
        query = query == null ? new Query() : query;
        // need _t field else deserializer won't know which class to deserialize into
        query.withIncludeFields(List.of(
                "_t",
                BaseEntity.FIELD_ID,
                BaseEntity.FIELD_CREATED_TIME,
                BaseEntity.FIELD_UPDATED_TIME,
                NamedEntity.FIELD_NAME,
                BaseAgentConfig.FIELD_DESCRIPTION));
        return findAssets(request);
    }
}

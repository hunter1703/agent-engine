package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetHandler;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class AgentAssetHandler implements AssetHandler<AgentConfig> {
    
    private static final String ASSET_TYPE = "agent";
    
    private final AgentRepository agentRepository;
    
    @Inject
    public AgentAssetHandler(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }
    
    @Override
    public String getAssetType() {
        return ASSET_TYPE;
    }
    
    @Override
    public PaginatedResult<AgentConfig> findAssets(AssetRequest request) {
        return agentRepository.findByQuery(request.getQuery());
    }

    @Override
    public Map<String, AgentConfig> getAssetsByIds(AssetRequest request) {
        Map<String, AgentConfig> result = new HashMap<>();
        if (request.getKeys() == null || request.getKeys().isEmpty()) {
            return result;
        }
        
        for (String key : request.getKeys()) {
            agentRepository.findById(key).ifPresent(value -> result.put(key, value));
        }
        
        return result;
    }
}
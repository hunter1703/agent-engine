package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.repository.SessionRepository;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetHandler;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SessionAssetHandler implements AssetHandler<AgentSession> {
    
    private static final String ASSET_TYPE = "session";
    
    private final SessionRepository sessionRepository;
    
    @Inject
    public SessionAssetHandler(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }
    
    @Override
    public String getAssetType() {
        return ASSET_TYPE;
    }
    
    @Override
    public PaginatedResult<AgentSession> findAssets(AssetRequest request) {
        return sessionRepository.findByQuery(request.getQuery());
    }
    
    @Override
    public Map<String, AgentSession> getAssetsByIds(AssetRequest request) {
        Map<String, AgentSession> result = new HashMap<>();
        if (request.getKeys() == null || request.getKeys().isEmpty()) {
            return result;
        }

        for (String key : request.getKeys()) {
            sessionRepository.findById(key).ifPresent(value -> result.put(key, value));
        }

        return result;
    }
}
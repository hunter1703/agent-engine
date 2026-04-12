package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.core.api.services.SessionService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SessionAssetHandler extends NamedAssetHandler<AgentSession> {

    public static final String INCLUDE_EVENTS_OPTION = "includeEvents";

    private final SessionService sessionService;

    @Inject
    public SessionAssetHandler(final SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public String getAssetType() {
        return AssetClass.AGENT_SESSION;
    }

    @Override
    public PaginatedResult<AgentSession> findAssets(final AssetRequest request) {
        final Query query = sanitizedQuery(request);
        return sessionService.findSessions(query);
    }

    @Override
    public PaginatedResult<AgentSession> listAssets(final AssetRequest request) {
        Query query = sanitizedQuery(request);
        query.withIncludeFields(List.of(
                BaseEntity.FIELD_ID,
                BaseEntity.FIELD_CREATED_TIME,
                BaseEntity.FIELD_UPDATED_TIME,
                NamedEntity.FIELD_NAME,
                AgentSession.FIELD_AGENT_ID));
        return findAssets(request);
    }

    @Override
    public Map<String, AgentSession> getAssetsByIds(final AssetRequest request) {
        final Map<String, AgentSession> result = new HashMap<>();
        if (request.getKeys() == null || request.getKeys().isEmpty()) {
            return result;
        }

        final boolean includeEvents = shouldIncludeEvents(request);
        return sessionService.getSessions(request.getKeys(), includeEvents);
    }

    private static boolean shouldIncludeEvents(final AssetRequest request) {
        if (request == null) {
            return false;
        }
        return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(request.getOptions(), INCLUDE_EVENTS_OPTION));
    }

    private static Query sanitizedQuery(final AssetRequest request) {
        final Query query = request == null || request.getQuery() == null ? new Query() : request.getQuery();
        Filter filter = query.getFilter();
        final Filter rootSession = Filters.notExists(AgentSession.FIELD_PARENT_SESSION_ID);
        filter = filter == null ? rootSession : Filters.and(filter, rootSession);
        return query.withFilter(filter);
    }
}

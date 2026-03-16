package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.NamedEntity;
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
  private static final String ASSET_TYPE = "session";

  private final SessionService sessionService;

  @Inject
  public SessionAssetHandler(final SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @Override
  public String getAssetType() {
    return ASSET_TYPE;
  }

  @Override
  public PaginatedResult<AgentSession> findAssets(final AssetRequest request) {
    final boolean includeEvents = shouldIncludeEvents(request);
    final Query query = buildQuery(request, includeEvents);
    return sessionService.findSessions(query);
  }

  @Override
  public PaginatedResult<AgentSession> listAssets(final AssetRequest request) {
    Query query = request.getQuery();
    query = query == null ? new Query() : query;
    query.withIncludeFields(
        List.of(BaseEntity.FIELD_ID, BaseEntity.FIELD_CREATED_TIME, BaseEntity.FIELD_UPDATED_TIME, NamedEntity.FIELD_NAME));
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

  private static Query buildQuery(final AssetRequest request, final boolean includeEvents) {
    final Query query = request == null || request.getQuery() == null ? new Query() : request.getQuery();
    if (includeEvents) {
      return query.addIncludeField(AgentSession.FIELD_EVENTS);
    }
    if (CollectionUtils.nullSafeList(query.getIncludeFields()).isEmpty()) {
      final List<String> excluded = CollectionUtils.nullSafeMutableList(query.getExcludeFields());
      if (!excluded.contains(AgentSession.FIELD_EVENTS)) {
        excluded.add(AgentSession.FIELD_EVENTS);
      }
      query.setExcludeFields(excluded);
    }
    return query;
  }
}

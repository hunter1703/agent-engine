package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.catalog.NamedAssetHandler;
import com.agentengine.interfaces.rest.dto.AgentSessionDTO;
import com.agentengine.interfaces.rest.handlers.AGUIEventMapper;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agui.core.event.BaseEvent;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SessionAssetHandler extends NamedAssetHandler<AgentSessionDTO> {

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
  public PaginatedResult<AgentSessionDTO> findAssets(final AssetRequest request) {
    final boolean includeEvents = shouldIncludeEvents(request);
    final Query query = buildQuery(request, includeEvents);
    final PaginatedResult<AgentSession> result = sessionService.findSessions(query);
    return result.transform(
        session -> includeEvents ? attachEvents(session) : new AgentSessionDTO(session, List.of()));
  }

  @Override
  public PaginatedResult<AgentSessionDTO> listAssets(final AssetRequest request) {
    final PaginatedResult<AgentSessionDTO> assets = findAssets(request);
    return assets.transform(this::toSessionAssetSummary);
  }

  @Override
  public Map<String, AgentSessionDTO> getAssetsByIds(final AssetRequest request) {
    final Map<String, AgentSessionDTO> result = new HashMap<>();
    if (request.getKeys() == null || request.getKeys().isEmpty()) {
      return result;
    }

    final boolean includeEvents = shouldIncludeEvents(request);
    final Map<String, AgentSession> sessionMap = sessionService.getSessions(request.getKeys());
    for (final AgentSession session : sessionMap.values()) {
      final AgentSessionDTO dto =
          includeEvents ? attachEvents(session) : new AgentSessionDTO(session, List.of());
      result.put(session.getId(), dto);
    }

    return result;
  }

  private AgentSessionDTO toSessionAssetSummary(final AgentSessionDTO session) {
    if (session == null) {
      return null;
    }
    return AgentSessionDTO.summary(session);
  }

  private AgentSessionDTO attachEvents(final AgentSession session) {
    if (session == null) {
      return null;
    }
    final List<BaseEvent> events =
        mapAguiEvents(
            session.getAgentId(), session.getId(), session.getSessionInfo().toSession().events());
    return new AgentSessionDTO(session, events);
  }

  private static List<BaseEvent> mapAguiEvents(
      final String agentId, final String sessionId, final List<Event> events) {
    if (CollectionUtils.isEmpty(events)) {
      return List.of();
    }
    final AGUIEventMapper mapper = new AGUIEventMapper(sessionId, agentId);
    return Flowable.fromIterable(events)
        .concatMap(mapper::map)
        .concatWith(mapper.onComplete())
        .toList()
        .blockingGet();
  }

  private static boolean shouldIncludeEvents(final AssetRequest request) {
    if (request == null) {
      return false;
    }
    return Boolean.TRUE.equals(
        CollectionUtils.getBooleanValueFromMap(request.getOptions(), INCLUDE_EVENTS_OPTION));
  }

  private static Query buildQuery(final AssetRequest request, final boolean includeEvents) {
    final Query source =
        request == null || request.getQuery() == null ? new Query() : request.getQuery();
    if (includeEvents) {
      return source;
    }
    final Query query = new Query();
    query.setFilter(source.getFilter());
    query.setPage(source.getPage());
    query.setSort(source.getSort());
    query.setIncludeCount(source.isIncludeCount());
    query.setIncludeFields(source.getIncludeFields());

    final List<String> excluded =
        new ArrayList<>(CollectionUtils.nullSafeList(source.getExcludeFields()));
    if (!excluded.contains("sessionInfo.eventsJson")) {
      excluded.add("sessionInfo.eventsJson");
    }
    query.setExcludeFields(excluded);
    return query;
  }
}

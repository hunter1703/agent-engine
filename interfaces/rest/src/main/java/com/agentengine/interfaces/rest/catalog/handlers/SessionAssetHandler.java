package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.NamedAssetHandler;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.dto.AgentSessionDTO;
import com.agentengine.interfaces.rest.handlers.AGUIEventMapper;
import com.agui.core.event.BaseEvent;
import com.google.adk.events.Event;
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
    //TODO: do not fetch not needed fields
    final PaginatedResult<AgentSession> result = sessionService.findSessions(request.getQuery());
    return result.transform(session -> {
      final AgentSessionDTO dto = shouldIncludeEvents(request)
              ? attachEvents(session)
              : new AgentSessionDTO(session, List.of());
      dto.setSessionInfo(null);
      return dto;
    });
  }

  @Override
  public Map<String, AgentSessionDTO> getAssetsByIds(final AssetRequest request) {
    final Map<String, AgentSessionDTO> result = new HashMap<>();
    if (request.getKeys() == null || request.getKeys().isEmpty()) {
      return result;
    }

    final boolean includeEvents = shouldIncludeEvents(request);
    for (final String key : request.getKeys()) {
      sessionService.getSession(key)
          .map(session -> {
            final AgentSessionDTO dto = includeEvents ? attachEvents(session) : new AgentSessionDTO(session, List.of());
            dto.setSessionInfo(null);
            return dto;
          }).ifPresent(value -> result.put(key, value));
    }

    return result;
  }

  @Override
  protected String getName(AgentSessionDTO asset) {
    return asset.getTitle();
  }

  private AgentSessionDTO attachEvents(final AgentSession session) {
    if (session == null) {
      return null;
    }
    final List<BaseEvent> events = mapAguiEvents(session.getAgentId(), session.getId(),
        session.getSessionInfo().toSession().events());
    return new AgentSessionDTO(session, events);
  }

  private static List<BaseEvent> mapAguiEvents(final String agentId, final String sessionId, final List<Event> events) {
    if (CollectionUtils.isEmpty(events)) {
      return List.of();
    }
    final List<BaseEvent> mappedEvents = new ArrayList<>();
    final AGUIEventMapper mapper = new AGUIEventMapper(sessionId, agentId);
    for (final Event event : events) {
      mappedEvents.addAll(mapper.map(event).toList().blockingGet());
    }
    mappedEvents.addAll(mapper.onComplete().toList().blockingGet());
    return mappedEvents;
  }

  private static boolean shouldIncludeEvents(final AssetRequest request) {
    if (request == null) {
      return false;
    }
    return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(request.getOptions(), INCLUDE_EVENTS_OPTION));
  }
}

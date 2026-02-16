package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.beans.session.AgentSession;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetHandler;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.dto.AgentSessionDTO;
import com.agentengine.interfaces.rest.handlers.AGUIEventMapper;
import com.agui.core.event.BaseEvent;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Singleton
public class SessionAssetHandler implements AssetHandler<AgentSessionDTO> {

  private static final String ASSET_TYPE = "session";
  private static final String INCLUDE_EVENTS_OPTION = "includeEvents";

  private final AgentSessionRepository agentSessionRepository;

  @Inject
  public SessionAssetHandler(final AgentSessionRepository agentSessionRepository) {
    this.agentSessionRepository = agentSessionRepository;
  }

  @Override
  public String getAssetType() {
    return ASSET_TYPE;
  }

  @Override
  public PaginatedResult<AgentSessionDTO> findAssets(final AssetRequest request) {
    final PaginatedResult<AgentSession> result = agentSessionRepository.findByQuery(request.getQuery());
    return result.transform(session -> {
      final AgentSessionDTO dto = shouldIncludeEvents(request) ? attachEvents(session) : new AgentSessionDTO(session, List.of());
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
      agentSessionRepository.findById(key)
          .map(session -> includeEvents ? attachEvents(session) : new AgentSessionDTO(session, List.of()))
          .ifPresent(value -> result.put(key, value));
    }

    return result;
  }

  private AgentSessionDTO attachEvents(final AgentSession session) {
    if (session == null) {
      return null;
    }
    final List<BaseEvent> events = mapAguiEvents(session.getAgentId(), session.getId(), session.getSessionInfo().toSession().events());
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

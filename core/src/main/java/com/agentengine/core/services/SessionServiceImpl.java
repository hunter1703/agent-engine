package com.agentengine.core.services;

import com.agentengine.core.agui.AGUIEventMapper;
import com.agentengine.core.api.services.SessionService;
import com.agentengine.core.events.SessionDeletedEvent;
import com.agentengine.core.repository.SessionRepository;
import com.agentengine.runtime.actor.SessionEvent;
import com.agentengine.runtime.actor.SessionHistory;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agui.core.event.BaseEvent;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Singleton
@Unremovable
public class SessionServiceImpl implements SessionService {

  private final SessionRepository sessionRepository;
  private final Event<SessionDeletedEvent> sessionDeletedEvent;
  private final SessionHistory sessionHistory;

  @Inject
  public SessionServiceImpl(final SessionRepository sessionRepository, final Event<SessionDeletedEvent> sessionDeletedEvent,
      final SessionHistory sessionHistory) {
    this.sessionRepository = sessionRepository;
    this.sessionDeletedEvent = sessionDeletedEvent;
    this.sessionHistory = sessionHistory;
  }

  @Override
  @WithSpan
  public AgentSession getSession(final String id) {
    return getSession(id, false);
  }

  @Override
  public AgentSession getSession(final String id, final boolean includeEvents) {
    return sanitizeSession(sessionRepository.findById(id), includeEvents);
  }

  @Override
  public Map<String, AgentSession> getSessions(final Collection<String> ids) {
    return getSessions(ids, false);
  }

  @Override
  public Map<String, AgentSession> getSessions(final Collection<String> ids, final boolean includeEvents) {
    final Map<String, AgentSession> idVsSession = sessionRepository.findByIds(ids);
    idVsSession.values().forEach(s -> sanitizeSession(s, includeEvents));
    return idVsSession;
  }

  @Override
  @WithSpan
  public PaginatedResult<AgentSession> findSessions(final Query query) {
    return sessionRepository.findByQuery(query).transform(s -> sanitizeSession(s, false));
  }

  @Override
  @WithSpan
  public boolean deleteSession(final String id) {
    final boolean deleted = sessionRepository.deleteById(id);
    if (deleted) {
      sessionDeletedEvent.fire(new SessionDeletedEvent(id));
    }
    return deleted;
  }

  private AgentSession sanitizeSession(final AgentSession session, final boolean includeEvents) {
    if (!includeEvents || session == null) {
      return session;
    }
    final AGUIEventMapper mapper = new AGUIEventMapper(session.getId(), session.getAgentId(), AGUIEventMapper.Mode.REPLAY);
    final List<BaseEvent> aguiEvents = new ArrayList<>();
    for (final SessionEvent event : sessionHistory.events(session.getId())) {
      mapper.map(event).blockingForEach(aguiEvents::add);
    }
    session.setAguiEvents(aguiEvents);
    return session;
  }
}

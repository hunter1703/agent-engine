package com.agentengine.engine.services;

import com.agentengine.core.api.services.SessionService;
import com.agentengine.engine.agui.AGUIEventMapper;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.beans.session.SessionInfo;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.events.SessionDeletedEvent;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.runtime.api.repository.SessionRepository;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.session.SessionInfo;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.EncryptionService;
import com.agentengine.util.common.Utils;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agui.core.event.BaseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class SessionServiceImpl implements SessionService {
  private static final Logger LOG = LoggerFactory.getLogger(SessionServiceImpl.class);

  private final SessionRepository sessionRepository;
  private final EncryptionService encryptionService;
  private final Event<SessionDeletedEvent> sessionDeletedEvent;

  @Inject
  public SessionServiceImpl(SessionRepository sessionRepository, EncryptionService encryptionService,
                            Event<SessionDeletedEvent> sessionDeletedEvent) {
    this.sessionRepository = sessionRepository;
    this.encryptionService = encryptionService;
    this.sessionDeletedEvent = sessionDeletedEvent;
  }

  @Override
  @WithSpan
  public AgentSession getSession(String id) {
    return getSession(id, false);
  }

  @Override
  public AgentSession getSession(final String id, final boolean includeEvents) {
    return sanitizeSession(sessionRepository.findById(id, List.of(), includeEvents ? List.of() : List.of(AgentSession.FIELD_EVENTS)));
  }

  @Override
  public Map<String, AgentSession> getSessions(final Collection<String> ids) {
    return getSessions(ids, false);
  }

  @Override
  public Map<String, AgentSession> getSessions(final Collection<String> ids, final boolean includeEvents) {
    final Map<String, AgentSession> idVsSession = sessionRepository.findByIds(ids, List.of(),
            includeEvents ? List.of() : List.of(AgentSession.FIELD_EVENTS));
    idVsSession.values().forEach(SessionServiceImpl::sanitizeSession);
    return idVsSession;
  }

  @Override
  @WithSpan
  public PaginatedResult<AgentSession> findSessions(Query query) {
    return sessionRepository.findByQuery(query).transform(SessionServiceImpl::sanitizeSession);
  }

  @Override
  @WithSpan
  public boolean deleteSession(String id) {
    final boolean deleted = sessionRepository.deleteById(id);
    if (deleted) {
      sessionDeletedEvent.fire(new SessionDeletedEvent(id));
    }
    return deleted;
  }

  private static AgentSession sanitizeSession(final AgentSession session) {
    if (session == null) {
      return null;
    }
    final SessionInfo sessionInfo = session.getSessionInfo();
    if (sessionInfo != null) {
      final AGUIEventMapper mapper = new AGUIEventMapper(session.getId(), session.getAgentId(), AGUIEventMapper.Mode.REPLAY);
      final TypeReference<com.google.adk.events.Event> typeReference = new TypeReference<>() {
      };
      final List<BaseEvent> aguiEvents = Flowable.concat(CollectionUtils.nullSafeList(sessionInfo.getEvents()).stream()
              .map(event -> mapper.map(Utils.toType(event, typeReference))).toList()).toList().blockingGet();
      session.setAguiEvents(aguiEvents);
    }
    return session;
  }
}

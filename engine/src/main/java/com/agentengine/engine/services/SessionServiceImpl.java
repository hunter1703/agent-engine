package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.util.query.PaginatedResult;
import com.agentengine.util.query.Query;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.util.update.Operation;
import com.agentengine.util.update.Update;
import com.agentengine.engine.events.SessionDeletedEvent;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.util.EncryptionService;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class SessionServiceImpl implements SessionService {
  private static final Logger LOG = LoggerFactory.getLogger(SessionServiceImpl.class);

  private final AgentSessionRepository sessionRepository;
  private final EncryptionService encryptionService;
  private final Event<SessionDeletedEvent> sessionDeletedEvent;

  @Inject
  public SessionServiceImpl(
      AgentSessionRepository sessionRepository,
      EncryptionService encryptionService,
      Event<SessionDeletedEvent> sessionDeletedEvent) {
    this.sessionRepository = sessionRepository;
    this.encryptionService = encryptionService;
    this.sessionDeletedEvent = sessionDeletedEvent;
  }

  @Override
  @WithSpan
  public Optional<AgentSession> getSession(String id) {
    return sessionRepository.findById(id);
  }

  @Override
  public Map<String, AgentSession> getSessions(final Collection<String> ids) {
    return sessionRepository.findByIds(ids);
  }

  @Override
  @WithSpan
  public PaginatedResult<AgentSession> findSessions(final Query query) {
    return sessionRepository.findByQuery(query);
  }

  @Override
  @WithSpan
  public void deleteSession(String id) {
    sessionRepository.deleteById(id);
    sessionDeletedEvent.fire(new SessionDeletedEvent(id));
  }

  @Override
  @WithSpan
  public void updateTitle(final String id, final String title) {
    sessionRepository.update(id, Update.of(Operation.set("title", encryptionService.encrypt(title))));
  }
}

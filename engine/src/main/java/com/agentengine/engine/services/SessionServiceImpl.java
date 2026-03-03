package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.update.Operation;
import com.agentengine.engine.api.update.Update;
import com.agentengine.engine.repository.AgentSessionRepository;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

@Singleton
@Unremovable
public class SessionServiceImpl implements SessionService {

  private final AgentSessionRepository sessionRepository;

  @Inject
  public SessionServiceImpl(AgentSessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @Override
  @WithSpan
  public Optional<AgentSession> getSession(String id) {
    return sessionRepository.findById(id);
  }

  @Override
  @WithSpan
  public PaginatedResult<AgentSession> findSessions(Query query) {
    return sessionRepository.findByQuery(query);
  }

  @Override
  @WithSpan
  public void deleteSession(String id) {
    sessionRepository.deleteById(id);
  }

  @Override
  @WithSpan
  public void updateTitle(final String id, final String title) {
    sessionRepository.update(id, Update.of(Operation.set("title", title)));
  }
}

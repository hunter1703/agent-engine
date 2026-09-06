package com.agentengine.catalog.core.services;

import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.catalog.core.repository.SessionRepository;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.Map;

@Singleton
@Unremovable
public class SessionServiceImpl implements SessionService {

  private final SessionRepository sessionRepository;

  @Inject
  public SessionServiceImpl(final SessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @Override
  @WithSpan
  public AgentSession getSession(final String id) {
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
  public boolean deleteSession(final String id) {
    return sessionRepository.deleteById(id);
  }

  @Override
  public AgentSession updateSession(final String id, final Update update) {
    return sessionRepository.update(id, update);
  }

  @Override
  public long updateSessions(final Query query, final Update update) {
    return sessionRepository.updateMany(query.getFilter(), update);
  }

  @Override
  public AgentSession create(final AgentSession session) {
    return sessionRepository.insert(session);
  }
}

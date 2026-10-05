package com.agentengine.catalog.core.services;

import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.catalog.core.repository.SessionRepository;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.context.Context;
import com.agentengine.util.tenancy.AclService;
import com.agentengine.util.tenancy.Permission;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Singleton
@Unremovable
public class SessionServiceImpl implements SessionService {

  private final SessionRepository sessionRepository;
  private final AclService aclService;

  @Inject
  public SessionServiceImpl(
      final SessionRepository sessionRepository, final AclService aclService) {
    this.sessionRepository = sessionRepository;
    this.aclService = aclService;
  }

  @Override
  @WithSpan
  public AgentSession getSession(final String id) {
    return sessionRepository.findById(id);
  }

  @Override
  @WithSpan
  public AgentSession getSession(final String id, final List<String> includeFields) {
    return sessionRepository.findById(id, includeFields, null);
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
    AgentSession session =
        sessionRepository.findById(id, List.of(AgentSession.FIELD_AGENT_ID), null);
    final boolean deleted = sessionRepository.deleteByIdIgnoringVersion(id);
    if (deleted) {
      final PaginatedResult<AgentSession> children =
          sessionRepository.findByQuery(
              new Query().withFilter(Filters.eq(AgentSession.FIELD_PARENT_SESSION_ID, id)));
      for (final AgentSession child : children.getItems()) {
        deleteSession(child.getId());
      }

      Context.require()
          .asSystemCaller()
          .run(
              () ->
                  aclService.forgetPrincipal(
                      AgentSession.principal(session.getAgentId(), id).toString()));
    }
    return deleted;
  }

  @Override
  public AgentSession updateSession(final String id, final Update update) {
    return sessionRepository.updateIgnoringVersion(id, update);
  }

  @Override
  public long updateSessions(final Query query, final Update update) {
    return sessionRepository.updateManyIgnoringVersion(query.getFilter(), update);
  }

  @Override
  public AgentSession create(final AgentSession session) {
    return sessionRepository.insert(session);
  }

  @Override
  public boolean hasPermission(final String id, final Permission permission) {
    return sessionRepository.hasPermission(id, permission);
  }
}

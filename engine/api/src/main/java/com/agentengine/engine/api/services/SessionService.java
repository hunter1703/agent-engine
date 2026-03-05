package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.ms.MicroService;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import java.util.Optional;

@MicroService
public interface SessionService {
  Optional<AgentSession> getSession(String id);

  PaginatedResult<AgentSession> findSessions(Query query);

  default PaginatedResult<AgentSession> findSessions(final Query query, final boolean includeEvents) {
    return findSessions(query);
  }

  void deleteSession(String id);

  void updateTitle(String id, String title);
}

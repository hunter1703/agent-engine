package com.agentengine.engine.api.services;

import com.agentengine.engine.api.MicroService;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.utils.PaginatedResult;
import java.util.Optional;

@MicroService
public interface SessionService {
  Optional<AgentSession> getSession(String id);

  PaginatedResult<AgentSession> findSessions(Query query);

  void deleteSession(String id);
}

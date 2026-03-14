package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.MicroService;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@MicroService("agent")
public interface SessionService {
  Optional<AgentSession> getSession(String id);

  Optional<AgentSession> getSession(String id, boolean includeEvents);

  Map<String, AgentSession> getSessions(Collection<String> ids);

  Map<String, AgentSession> getSessions(Collection<String> ids, boolean includeEvents);

  PaginatedResult<AgentSession> findSessions(Query query);

  boolean deleteSession(String id);

  void updateTitle(String id, String title);
}

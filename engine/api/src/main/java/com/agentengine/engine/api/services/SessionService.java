package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.util.ms.MicroService;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@MicroService("agent")
public interface SessionService {
  Optional<AgentSession> getSession(String id);

  Map<String, AgentSession> getSessions(Collection<String> ids);

  PaginatedResult<AgentSession> findSessions(Query query);

  void deleteSession(String id);

  void updateTitle(String id, String title);
}

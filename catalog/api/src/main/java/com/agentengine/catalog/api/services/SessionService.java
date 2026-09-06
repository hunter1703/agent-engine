package com.agentengine.catalog.api.services;

import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.ms.client.MicroService;
import java.util.Collection;
import java.util.Map;

@MicroService("catalog")
public interface SessionService {
  AgentSession getSession(String id);

  Map<String, AgentSession> getSessions(Collection<String> ids);

  PaginatedResult<AgentSession> findSessions(Query query);

  boolean deleteSession(String id);

  AgentSession updateSession(String id, Update update);

  long updateSessions(Query query, Update update);

  AgentSession create(AgentSession session);
}

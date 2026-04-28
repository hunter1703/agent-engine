package com.agentengine.catalog.api.services;

import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.ms.MicroService;
import java.util.Collection;
import java.util.Map;

@MicroService("agent")
public interface SessionService {
    AgentSession getSession(String id);

    AgentSession getSession(String id, boolean includeEvents);

    Map<String, AgentSession> getSessions(Collection<String> ids);

    Map<String, AgentSession> getSessions(Collection<String> ids, boolean includeEvents);

    PaginatedResult<AgentSession> findSessions(Query query);

    boolean deleteSession(String id);

    AgentSession updateSession(String id, Update update);

    AgentSession create(AgentSession session);
}

package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.utils.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import java.util.Optional;

public interface SessionService {
    Optional<AgentSession> getSession(String id);

    PaginatedResult<AgentSession> findSessions(Query query);

    void deleteSession(String id);
}

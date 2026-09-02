package com.agentengine.util.agents.repository;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.repository.Repository;
import com.google.adk.events.Event;
import java.util.List;

public interface SessionEventsRepository extends Repository<SessionEvent> {

  /** Returns the events committed under {@code turnId}, in sequence order. */
  List<Event> findTurnEvents(String sessionId, String turnId);

  /**
   * Returns the canonical events for {@code sessionId} — or, when {@code includeChildSessions} is
   * true, for the whole session tree rooted at it (root and every child session).
   */
  List<SessionEvent> getCommittedSessionEvents(String sessionId, boolean includeChildSessions);
}

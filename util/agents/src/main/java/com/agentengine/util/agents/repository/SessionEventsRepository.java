package com.agentengine.util.agents.repository;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.repository.Repository;
import com.google.adk.events.Event;
import java.util.List;

public interface SessionEventsRepository extends Repository<SessionEvent> {

  /** Returns the events committed under {@code turnId}, in sequence order. */
  List<Event> findTurnEvents(String sessionId, String turnId);

  /**
   * Returns stored events for {@code sessionId} whose turn is in {@code committedTurnIds} (or has
   * no turn at all) — or, when {@code includeChildSessions} is true, the same for the whole session
   * tree rooted at it (root and every child session).
   */
  List<SessionEvent> getCommittedSessionEvents(
      String sessionId, List<String> committedTurnIds, boolean includeChildSessions);
}

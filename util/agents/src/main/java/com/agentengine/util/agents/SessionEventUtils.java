package com.agentengine.util.agents;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.events.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SessionEventUtils {

  public static final String VIOLATION = "violation";
  public static final String INTERNAL = "internal";
  public static final String SESSION_ID = "sessionId";
  public static final String ATTACHMENTS = "attachments";

  private SessionEventUtils() {}

  public static boolean isCorrectionEvent(final SessionEvent event) {
    final Map<String, Object> metadata =
        CollectionUtils.nullSafeMap(event == null ? null : event.getMetadata());
    return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, VIOLATION));
  }

  public static boolean isInternal(final SessionEvent event) {
    final Map<String, Object> metadata =
        CollectionUtils.nullSafeMap(event == null ? null : event.getMetadata());
    return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, INTERNAL));
  }

  public static List<SessionEvent> toSessionEvents(
      final String rootSessionId,
      final String parentSessionId,
      final String sessionId,
      final String turnId,
      final List<Event> events,
      final long startSequence) {
    if (CollectionUtils.isEmpty(events)) {
      return List.of();
    }
    final List<SessionEvent> sessionEvents = new ArrayList<>(events.size());
    for (int index = 0; index < events.size(); index++) {
      sessionEvents.add(
          toSessionEvent(
              rootSessionId,
              parentSessionId,
              sessionId,
              turnId,
              events.get(index),
              startSequence + index));
    }
    return sessionEvents;
  }

  public static SessionEvent toSessionEvent(
      final String rootSessionId,
      final String parentSessionId,
      final String sessionId,
      final String turnId,
      final Event event,
      final long sequence) {
    return new SessionEvent(
        event.id(),
        rootSessionId,
        parentSessionId,
        sessionId,
        sequence,
        SessionEvent.Type.NORMAL,
        turnId,
        event);
  }
}

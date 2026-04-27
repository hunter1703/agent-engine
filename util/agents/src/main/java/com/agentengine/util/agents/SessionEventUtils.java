package com.agentengine.util.agents;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.events.Event;
import com.google.adk.sessions.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class SessionEventUtils {

    public static final String VIOLATION = "violation";
    public static final String INTERNAL = "internal";
    public static final String SESSION_ID = "sessionId";
    public static final String ATTACHMENTS = "attachments";

    private SessionEventUtils() {}

    public static boolean isCorrectionEvent(final SessionEvent event) {
        final Map<String, Object> metadata = CollectionUtils.nullSafeMap(event == null ? null : event.getMetadata());
        return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, VIOLATION));
    }

    public static boolean isInternal(final SessionEvent event) {
        final Map<String, Object> metadata = CollectionUtils.nullSafeMap(event == null ? null : event.getMetadata());
        return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, INTERNAL));
    }

    public static SessionEvent toSessionEvent(
            final String rootSessionId, final String parentSessionId, final String sessionId, final Event event) {
        return toSessionEvent(rootSessionId, parentSessionId, sessionId, event, 0L);
    }

    public static SessionEvent toSessionEvent(
            final String rootSessionId,
            final String parentSessionId,
            final String sessionId,
            final Event event,
            final long sequence) {
        return new SessionEvent(
                event.id(),
                rootSessionId,
                parentSessionId,
                sessionId,
                event.invocationId(),
                event.author(),
                event.content().orElse(null),
                event.partial().orElse(false),
                event.turnComplete().orElse(false),
                event.finishReason().orElse(null),
                event.timestamp(),
                sequence,
                extractMetadata(event),
                SessionEvent.Type.NORMAL);
    }

    public static List<SessionEvent> toSessionEvents(
            final String rootSessionId,
            final String parentSessionId,
            final String sessionId,
            final List<Event> events,
            final long startSequence) {
        if (CollectionUtils.isEmpty(events)) {
            return List.of();
        }
        final List<SessionEvent> sessionEvents = new ArrayList<>(events.size());
        for (int index = 0; index < events.size(); index++) {
            sessionEvents.add(toSessionEvent(
                    rootSessionId, parentSessionId, sessionId, events.get(index), startSequence + index));
        }
        return sessionEvents;
    }

    private static Map<String, Object> extractMetadata(final Event event) {
        final Map<String, Object> metadata = new HashMap<>();
        if (event.actions() != null && event.actions().stateDelta() != null) {
            for (final Entry<String, Object> entry :
                    event.actions().stateDelta().entrySet()) {
                // Strip the ADK State.TEMP_PREFIX ("temp:") so metadata keys are stored cleanly.
                String key = entry.getKey();
                key = key.startsWith(State.TEMP_PREFIX) ? key.substring(State.TEMP_PREFIX.length()) : key;
                metadata.put(key, entry.getValue());
            }
        }
        return metadata;
    }
}

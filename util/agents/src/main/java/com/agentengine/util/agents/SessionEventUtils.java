package com.agentengine.util.agents;

import com.agentengine.util.agents.beans.CorrectionMetadata;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.events.Event;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SessionEventUtils {

    public static final String CORRECTION = "correction";
    public static final String CORRECTION_TYPE = "correction_type";
    public static final String CORRECTION_CODE = "correction_code";
    public static final String CORRECTION_MESSAGE = "correction_message";
    public static final String INTERNAL = "internal";
    public static final String SESSION_ID = "sessionId";

    private SessionEventUtils() {}

    public static boolean isCorrectionEvent(final SessionEvent event) {
        final Map<String, Object> metadata = CollectionUtils.nullSafeMap(event == null ? null : event.getMetadata());
        return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, CORRECTION));
    }

    public static CorrectionMetadata extractCorrectionMetadata(final SessionEvent event) {
        if (!isCorrectionEvent(event)) {
            return null;
        }
        final Map<String, Object> metadata = CollectionUtils.nullSafeMap(event.getMetadata());
        final String type = CollectionUtils.getStringValueFromMap(metadata, CORRECTION);
        final String code = CollectionUtils.getStringValueFromMap(metadata, CORRECTION_TYPE);
        final String message = CollectionUtils.getStringValueFromMap(metadata, CORRECTION_MESSAGE);
        return new CorrectionMetadata(type, code, message);
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
                extractMetadata(event), event.finishReason().isPresent());
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
            metadata.putAll(event.actions().stateDelta());
        }
        return metadata;
    }
}

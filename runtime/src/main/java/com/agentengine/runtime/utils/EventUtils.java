package com.agentengine.runtime.utils;

import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.State;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Utilities for working with runtime {@link Event} objects. */
public final class EventUtils {
    /**
     * Key used to mark an event as internal (pipeline-only).
     *
     * <p>Uses the {@link State#TEMP_PREFIX} so the value is not merged into session state, but is
     * preserved in the event's {@code stateDelta} through serialisation round-trips.
     */
    public static final String INTERNAL_KEY = State.TEMP_PREFIX + SessionEventUtils.INTERNAL;

    private EventUtils() {}

    // not adding condition on event.finishReason() as ADK agentic loop ignores this
    // for termination
    // detection
    public static boolean isTerminal(final Event event) {
        if (event == null) {
            return false;
        }
        final boolean endInvocation =
                event.actions() != null && event.actions().endInvocation().orElse(false);
        return event.finalResponse() || endInvocation;
    }

    /**
     * Marks an event as internal so it is excluded from end-user-facing output (e.g. AG-UI events)
     * while remaining fully visible to the LLM as session history.
     */
    public static void markAsInternal(final Event event) {
        if (event == null) {
            return;
        }
        EventActions actions = event.actions();
        if (actions == null) {
            actions = new EventActions();
        }
        ConcurrentMap<String, Object> delta = actions.stateDelta();
        if (delta == null) {
            delta = new ConcurrentHashMap<>();
        }
        delta.put(INTERNAL_KEY, Boolean.TRUE);
        event.setActions(actions.toBuilder().stateDelta(delta).build());
    }

    /**
     * Scans events in reverse chronological order and returns the most recent value for {@code key}.
     * Returns {@code null} if the key has never been set or was explicitly removed via {@link
     * State#REMOVED}.
     */
    public static Object latestDeltaValue(final List<Event> events, final String key) {
        if (CollectionUtils.isEmpty(events) || StringUtils.isBlank(key)) {
            return null;
        }
        for (final Event event : events.reversed()) {
            if (event == null || event.actions() == null) {
                continue;
            }
            final Map<String, Object> delta = event.actions().stateDelta();
            if (CollectionUtils.isEmpty(delta) || !delta.containsKey(key)) {
                continue;
            }
            final Object value = delta.get(key);
            return State.REMOVED.equals(value) ? null : value;
        }
        return null;
    }
}

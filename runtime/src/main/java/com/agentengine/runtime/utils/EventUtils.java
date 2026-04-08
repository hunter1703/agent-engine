package com.agentengine.runtime.utils;

import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.sessions.State;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Utilities for working with runtime {@link Event} objects. */
public final class EventUtils {

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
        addMetadata(event, SessionEventUtils.INTERNAL, true);
    }

    public static void addMetadata(final Event event, final String key, final Object value) {
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
        delta.put(State.TEMP_PREFIX + key, value);
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

    public static String recentUser(final List<Event> events, final int max) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        final List<String> intents = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && intents.size() < max; i--) {
            final Content content = events.get(i).content().orElse(null);
            if (content == null || !"user".equals(content.role().orElse(""))) {
                continue;
            }
            final String text = content.text();
            if (StringUtils.isNotBlank(text)) {
                intents.add(text);
            }
        }
        return String.join("\n", intents.reversed());
    }

    public static Event buildConfirmationEvent(
            final String confirmationId, final Boolean confirmed, final Map<String, Object> answer) {
        final ToolConfirmation toolConfirmation = ResponseUtils.buildToolConfirmation(confirmed, answer);
        final FunctionResponse functionResponse = FunctionResponse.builder()
                .id(confirmationId)
                .name(Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
                .response(JsonUtils.toMap(toolConfirmation))
                .build();
        return Event.builder()
                .id(confirmationId)
                .author("user")
                .content(Content.builder()
                        .role("user")
                        .parts(List.of(Part.builder()
                                .functionResponse(functionResponse)
                                .build()))
                        .build())
                .build();
    }
}

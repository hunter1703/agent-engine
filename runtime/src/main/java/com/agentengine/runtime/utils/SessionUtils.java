package com.agentengine.runtime.utils;

import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionUtils {

    private SessionUtils() {}

    public static ConcurrentMap<String, Object> buildInitialState() {
        return new ConcurrentHashMap<>();
    }

    public static ConcurrentMap<String, Object> state(final InvocationContext context) {
        if (context == null || context.session() == null || context.session().state() == null) {
            return null;
        }
        return context.session().state();
    }

    public static Session toSession(final AgentSession agentSession, final List<Event> events) {
        if (agentSession == null) {
            return null;
        }
        final ConcurrentMap<String, Object> sessionState =
                new ConcurrentHashMap<>(CollectionUtils.nullSafeMap(agentSession.getState()));
        return Session.builder(agentSession.getId())
                .appName(agentSession.getAgentId())
                .userId(AgentSession.DEFAULT_USER_ID)
                .state(sessionState)
                .events(events == null ? new ArrayList<>() : new ArrayList<>(events))
                .lastUpdateTime(Instant.ofEpochMilli(agentSession.getUpdatedTime()))
                .build();
    }
}

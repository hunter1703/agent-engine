package com.agentengine.agent.infra.utils;

import static com.agentengine.agent.infra.utils.AgentUtils.getAgentIdFromContext;

import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.utils.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionUtils {

  private static final Logger LOG = LoggerFactory.getLogger(SessionUtils.class);

  private SessionUtils() {}

  public static ConcurrentMap<String, Object> buildInitialState() {
    return new ConcurrentHashMap<>();
  }

  public static SessionState getSessionState(final InvocationContext context) {
    return getSessionState(context, true);
  }

  public static SessionState getOrInitSessionState(final InvocationContext context) {
    final SessionState existing = getSessionState(context, false);
    if (existing != null) {
      return existing;
    }
    final SessionState sessionState = SessionState.buildFrom(context.session().events());
    final Map<String, Object> state = state(context);
    if (state != null) {
      // a session can have multiple agent states because a session can be shared by multiple
      // agents (like when AgentTransfer happens)
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, SessionState> sessionStates =
          (ConcurrentMap<String, SessionState>)
              state.computeIfAbsent(
                  "SESSION_STATES", _ -> new ConcurrentHashMap<String, SessionState>());
      sessionStates.put(getAgentIdFromContext(context), sessionState);
    }
    return sessionState;
  }

  public static boolean isNewRun(final InvocationContext context) {
    return context
        .userContent()
        .map(content -> !ContentUtils.isResumeContent(content))
        .orElse(true);
  }

  public static String newSessionId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static Map<String, Object> state(final InvocationContext context) {
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
        .userId(agentSession.getCreatedBy())
        .state(sessionState)
        .events(events == null ? new ArrayList<>() : new ArrayList<>(events))
        .lastUpdateTime(Instant.ofEpochMilli(agentSession.getUpdatedTime()))
        .build();
  }

  private static SessionState getSessionState(
      final InvocationContext context, boolean throwOnAbsent) {
    final String agentId = getAgentIdFromContext(context);
    final Map<String, Object> state = state(context);

    SessionState sessionState = null;
    if (state != null) {
      Map<String, SessionState> sessionStates =
          CollectionUtils.getMapFromMap(state, "SESSION_STATES");
      sessionState = CollectionUtils.getValueFromMap(sessionStates, agentId);
    }

    if (sessionState != null) {
      return sessionState;
    }

    if (throwOnAbsent) {
      throw new IllegalStateException(
          "Session state not created for session : " + context.session().id());
    }
    return null;
  }
}

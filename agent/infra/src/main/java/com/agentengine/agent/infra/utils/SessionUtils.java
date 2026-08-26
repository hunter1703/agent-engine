package com.agentengine.agent.infra.utils;

import static com.agentengine.agent.infra.utils.AgentUtils.getAgentIdFromContext;

import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
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

  public static SessionState getOrInitSessionState(
      final InvocationContext context,
      final KnowledgeService knowledgeService,
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository) {
    final SessionState existing = getSessionState(context, false);
    if (existing != null) {
      return existing;
    }
    final SessionState created =
        SessionState.buildFrom(
            context.session().events(), knowledgeService, notebookRepository, notesRepository);
    final String agentId = getAgentIdFromContext(context);
    final ConcurrentMap<String, Object> state = state(context);
    if (state != null) {
      // a session can have multiple agent states because a session can be shared by multiple agents
      // (like when AgentTransfer happens)
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, SessionState> sessionStates =
          (ConcurrentMap<String, SessionState>)
              state.computeIfAbsent(
                  "SESSION_STATES", _ -> new ConcurrentHashMap<String, SessionState>());
      sessionStates.put(agentId, created);
    }
    return created;
  }

  public static String newSessionId(final String agentId) {
    return agentId + Constants.ID_SEPARATOR + UUID.randomUUID();
  }

  public static String agentIdFromSessionId(final String sessionId) {
    if (StringUtils.isBlank(sessionId)) {
      return null;
    }
    final int separatorIndex = sessionId.indexOf(Constants.ID_SEPARATOR);
    return separatorIndex < 0 ? null : sessionId.substring(0, separatorIndex);
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

  private static SessionState getSessionState(
      final InvocationContext context, boolean throwOnAbsent) {
    final String agentId = getAgentIdFromContext(context);
    final ConcurrentMap<String, Object> state = state(context);

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

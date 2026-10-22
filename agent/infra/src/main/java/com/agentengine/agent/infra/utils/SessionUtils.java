package com.agentengine.agent.infra.utils;

import static com.agentengine.agent.infra.utils.AgentUtils.getAgentIdFromContext;

import com.agentengine.agent.infra.agents.ConfiguredAgent;
import com.agentengine.agent.infra.tools.planning.PlanningValidator;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.utils.CollectionUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Flowable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionUtils {

  private static final String SESSION_STATES_KEY = "SESSION_STATES";

  private SessionUtils() {}

  public static ConcurrentMap<String, Object> buildInitialState() {
    return new ConcurrentHashMap<>();
  }

  public static SessionState getSessionState(final InvocationContext context) {
    return getSessionState(context, true);
  }

  /**
   * Whether the run can be paused and resumed later. A run that cannot be resumed has nobody to
   * ask, so what would need a person's decision is refused instead.
   */
  public static boolean isResumable(final InvocationContext context) {
    final BaseAgentConfig config = agentConfig(context);
    return config == null || config.getRuntime().isResumable();
  }

  /** The config of the agent a callback runs in; null when it is not built from one. */
  public static BaseAgentConfig agentConfig(final InvocationContext context) {
    return context.agent() instanceof ConfiguredAgent agent ? agent.getAgentConfig() : null;
  }

  public static SessionState getOrInitSessionState(
      final InvocationContext context, final PlanningValidator planningValidator) {
    final SessionState existing = getSessionState(context, false);
    if (existing != null) {
      return existing;
    }
    final SessionState sessionState = SessionState.buildFrom(context.session(), planningValidator);
    final Map<String, Object> state = state(context);
    if (state != null) {
      // a session can have multiple agent states because a session can be shared by multiple
      // agents (like when AgentTransfer happens)
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, SessionState> sessionStates =
          (ConcurrentMap<String, SessionState>)
              state.computeIfAbsent(
                  SESSION_STATES_KEY, _ -> new ConcurrentHashMap<String, SessionState>());
      sessionStates.put(getAgentIdFromContext(context), sessionState);
    }
    return sessionState;
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

  /**
   * A context of no real run whose session state is {@code sessionState}, for driving tools outside
   * one.
   */
  public static InvocationContext throwawayContext(final SessionState sessionState) {
    final BaseAgent agent = new ThrowawayAgent();
    final ConcurrentMap<String, SessionState> sessionStates = new ConcurrentHashMap<>();
    sessionStates.put(agent.name(), sessionState);
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    state.put(SESSION_STATES_KEY, sessionStates);
    final Session session =
        Session.builder("throwaway").appName(agent.name()).userId("throwaway").state(state).build();
    return InvocationContext.builder()
        .sessionService(new InMemorySessionService())
        .agent(agent)
        .session(session)
        .build();
  }

  private static SessionState getSessionState(
      final InvocationContext context, boolean throwOnAbsent) {
    final String agentId = getAgentIdFromContext(context);
    final Map<String, Object> state = state(context);

    SessionState sessionState = null;
    if (state != null) {
      Map<String, SessionState> sessionStates =
          CollectionUtils.getMapFromMap(state, SESSION_STATES_KEY);
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

  private static final class ThrowawayAgent extends BaseAgent {
    private ThrowawayAgent() {
      super("throwaway", "", List.of(), List.of(), List.of());
    }

    @Override
    protected Flowable<Event> runAsyncImpl(final InvocationContext invocationContext) {
      return Flowable.empty();
    }

    @Override
    protected Flowable<Event> runLiveImpl(final InvocationContext invocationContext) {
      return Flowable.empty();
    }
  }
}

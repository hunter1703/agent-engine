package com.agentengine.engine.utils;

import com.google.adk.agents.InvocationContext;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public final class SessionStateUtils {
  private static final String SESSION_STATE_KEY = "session.state";

  private SessionStateUtils() {}

  public static SessionState getState(final InvocationContext context) {
    final ConcurrentMap<String, Object> state = state(context);
    final SessionState existing =
        TypedUtils.toType(state == null ? null : state.get(SESSION_STATE_KEY), SessionState.class);
    if (existing != null) {
      if (state.containsKey(SESSION_STATE_KEY)) {
        state.put(SESSION_STATE_KEY, existing);
      }
      return existing;
    }
    final SessionState created = new SessionState();
    if (state != null) {
      state.put(SESSION_STATE_KEY, created);
    }
    return created;
  }

  public static void pause(
      final InvocationContext context,
      final String prompt,
      final List<String> options,
      final String reason) {
    final ConcurrentMap<String, Object> state = state(context);
    if (state == null) {
      return;
    }
    getState(context).markPaused(
        prompt,
        options,
        reason, context.invocationId(),
        Instant.now().toEpochMilli());
  }

  public static void pause(final InvocationContext context, final String prompt, final String reason) {
    pause(context, prompt, List.of(), reason);
  }

  public static void resume(final InvocationContext context) {
    final ConcurrentMap<String, Object> state = state(context);
    if (state == null) {
      return;
    }
    getState(context).clearPause();
  }

  public static boolean isPaused(final InvocationContext context) {
    return getState(context).isPaused();
  }

  public static String getPausePrompt(final InvocationContext context) {
    return getState(context).pausePrompt();
  }

  public static List<String> getPauseOptions(final InvocationContext context) {
    return getState(context).pauseOptions();
  }

  public static String getPauseReason(final InvocationContext context) {
    return getState(context).pauseReason();
  }

  private static ConcurrentMap<String, Object> state(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return null;
    }
    return context.session().state();
  }
}

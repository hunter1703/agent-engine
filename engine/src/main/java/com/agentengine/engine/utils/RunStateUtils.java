package com.agentengine.engine.utils;

import com.google.adk.agents.InvocationContext;

import java.util.concurrent.ConcurrentMap;

public final class RunStateUtils {
  private static final String RUN_STATE_KEY = "run.state";

  private RunStateUtils() {}

  public static RunState getState(final InvocationContext context) {
    final Object stored = context.session().state().get(RUN_STATE_KEY);
    if (stored instanceof RunState runState) {
      return runState;
    }
    final RunState runState = new RunState();
    context.session().state().put(RUN_STATE_KEY, runState);
    return runState;
  }

  public static void clearState(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return;
    }
    context.session().state().remove(RUN_STATE_KEY);
  }

  public static void initState(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return;
    }
    final ConcurrentMap<String, Object> state = context.session().state();
    if (!state.containsKey(RUN_STATE_KEY)) {
      state.put(RUN_STATE_KEY, new RunState());
    }
  }

}

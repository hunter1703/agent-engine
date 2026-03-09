package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import java.util.concurrent.ConcurrentMap;

public final class RunStateUtils {

  private RunStateUtils() {}

  private static String runStateKey(final InvocationContext ctx) {
    final String agentId = (ctx != null && ctx.agent() != null) ? ctx.agent().name() : "unknown";
    return "run.state." + agentId;
  }

  public static RunState getState(final InvocationContext context) {
    final String key = runStateKey(context);
    final ConcurrentMap<String, Object> state = context.session().state();
    final RunState runState = CollectionUtils.getValueFromMap(state, key);
    if (runState == null) {
      state.put(key, new RunState());
    }
    return CollectionUtils.getValueFromMap(state, key);
  }

  public static void clearState(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return;
    }
    context.session().state().remove(runStateKey(context));
  }

  public static void initState(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return;
    }
    final String key = runStateKey(context);
    final ConcurrentMap<String, Object> state = context.session().state();
    if (!state.containsKey(key)) {
      state.put(key, new RunState());
    }
  }

}

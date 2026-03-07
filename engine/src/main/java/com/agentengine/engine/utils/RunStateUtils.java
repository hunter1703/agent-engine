package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public final class RunStateUtils {
  private static final String RUN_STATE_KEY = "run.state";

  private RunStateUtils() {}

  public static RunState getState(final InvocationContext context) {
    final ConcurrentMap<String, Object> state = context.session().state();
    final RunState runState = CollectionUtils.getValueFromMap(state, RUN_STATE_KEY);
    if (runState == null) {
      state.put(RUN_STATE_KEY, new RunState());
    }
    return CollectionUtils.getValueFromMap(state, RUN_STATE_KEY);
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

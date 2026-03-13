package com.agentengine.engine.utils;

import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.BaseAgentState;
import com.google.adk.agents.InvocationContext;
import java.util.Map;

public final class RunUtils {

  private RunUtils() {
  }

  public static RunState getState(final InvocationContext context) {
    final Map<String, BaseAgentState> agentStates = context.agentStates();
    final String agentId = agentId(context);
    final Object rawState = ((Map<?, ?>) agentStates).get(agentId);
    if (rawState instanceof RunState runState) {
      return runState;
    }
    final RunState created = RunState.buildFrom(context.session().events());
    agentStates.put(agentId, created);
    return created;
  }

  private static String agentId(final InvocationContext context) {
    if (context == null || context.agent() == null || StringUtils.isBlank(context.agent().name())) {
      return "unknown";
    }
    return context.agent().name();
  }
}

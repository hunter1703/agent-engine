package com.agentengine.agent.infra.utils;

import com.google.adk.agents.InvocationContext;

public final class RunUtils {

  private RunUtils() {}

  public static RunState getRunState(final InvocationContext context) {
    return SessionUtils.getSessionState(context).runState();
  }
}

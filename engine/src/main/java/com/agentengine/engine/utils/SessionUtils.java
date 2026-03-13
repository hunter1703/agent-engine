package com.agentengine.engine.utils;

import com.google.adk.agents.InvocationContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionUtils {
  private SessionUtils() {
  }

  public static ConcurrentMap<String, Object> buildInitialState() {
    return new ConcurrentHashMap<>();
  }

  public static ConcurrentMap<String, Object> state(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return null;
    }
    return context.session().state();
  }
}

package com.agentengine.engine.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionUtils {
  private SessionUtils() {}

  public static ConcurrentMap<String, Object> buildInitialState() {
    return new ConcurrentHashMap<>();
  }
}

package com.agentengine.connectors.core.runtime;

import java.util.Map;

public record Connection(String appName, Map<String, Object> inputs) {

  public Connection {
    appName = appName == null || appName.isBlank() ? null : appName;
    inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
  }
}

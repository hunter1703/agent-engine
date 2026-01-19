package com.agentengine.engine.tools;

import java.util.Map;

public interface Tool {
  String name();

  String description();

  String execute(Map<String, Object> args);

  default boolean retry(String status, String output) {
    return false;
  }
}

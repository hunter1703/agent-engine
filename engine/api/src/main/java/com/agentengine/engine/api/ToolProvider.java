package com.agentengine.engine.api;

import java.util.Map;

public interface ToolProvider {
  String agentId();

  String toolName();

  Tool create(Map<String, Object> toolConfig);
}

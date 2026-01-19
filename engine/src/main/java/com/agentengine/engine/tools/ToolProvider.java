package com.agentengine.engine.tools;

import java.util.Map;

public interface ToolProvider {
  String agentName();

  String toolName();

  Tool create(Map<String, Object> toolConfig);
}

package com.agentengine.engine.tools;

import java.util.Map;

public interface AgentTool {
  String name();

  String description();

  String execute(Map<String, Object> args);
}

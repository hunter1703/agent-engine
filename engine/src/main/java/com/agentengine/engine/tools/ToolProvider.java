package com.agentengine.engine.tools;

import com.agentengine.engine.beans.config.AgentConfig;
import java.util.Map;

public interface ToolProvider {
  String agentName();

  String toolName();

  AgentTool create(Map<String, Object> toolConfig, AgentConfig agentConfig);
}

package com.localagent.engine.tools;

import com.localagent.engine.beans.config.AgentConfig;
import java.util.Map;

public interface ToolProvider {
  String agentName();

  String toolName();

  AgentTool create(Map<String, Object> toolConfig, AgentConfig agentConfig);
}

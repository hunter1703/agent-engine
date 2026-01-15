package com.localagent.engine.tools;

import java.util.Map;

public interface ToolProvider {
    String agentName();

    String toolName();

    AgentTool create(Map<String, Object> toolConfig, Map<String, Object> agentConfig);
}

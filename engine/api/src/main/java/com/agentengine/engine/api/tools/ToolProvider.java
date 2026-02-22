package com.agentengine.engine.api.tools;

import com.agentengine.engine.api.AgentContext;
import com.google.adk.tools.BaseTool;
import java.util.List;
import java.util.Map;

public interface ToolProvider {

  List<ToolDescriptor> tools();

  Tool create(AgentContext agentContext, String toolName, Map<String, Object> toolConfig);
}

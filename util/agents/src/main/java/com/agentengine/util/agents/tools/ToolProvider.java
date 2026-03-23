package com.agentengine.util.agents.tools;

import com.google.adk.tools.BaseTool;
import java.util.Map;

public interface ToolProvider {

  ToolDescriptor descriptor();

  BaseTool create(Map<String, Object> toolConfig);
}

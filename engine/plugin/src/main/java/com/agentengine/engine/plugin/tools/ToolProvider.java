package com.agentengine.engine.plugin.tools;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.google.adk.tools.BaseTool;
import java.util.Map;

public interface ToolProvider {

  ToolDescriptor descriptor();

  BaseTool create(Map<String, Object> toolConfig);
}

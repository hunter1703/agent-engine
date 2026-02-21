package com.agentengine.engine.api.tools;

import com.agentengine.engine.api.AgentContext;
import com.google.adk.tools.BaseTool;
import java.util.Map;

public interface ToolProvider extends ToolAssetProvider {

  BaseTool create(AgentContext agentContext, Map<String, Object> toolConfig);

  default boolean isSubTool() {
    return false;
  }
}

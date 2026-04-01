package com.agentengine.runtime.tools;

import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.BaseTool;
import java.util.Map;

public interface ToolProvider {

    ToolDescriptor descriptor();

    BaseTool create(Map<String, Object> toolConfig);
}

package com.agentengine.runtime.tools;

import com.agentengine.util.agents.tools.ToolProvider;
import com.agentengine.util.agents.tools.ToolsetProvider;

public interface ToolService extends ToolCatalog {

  ToolProvider getToolProvider(String toolName);

  ToolsetProvider getToolsetProvider(String toolsetName);
}

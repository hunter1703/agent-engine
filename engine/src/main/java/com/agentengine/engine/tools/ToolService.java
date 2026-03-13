package com.agentengine.engine.tools;

import com.agentengine.engine.api.services.ToolCatalog;
import com.agentengine.engine.plugin.tools.ToolProvider;
import com.agentengine.engine.plugin.tools.ToolsetProvider;

public interface ToolService extends ToolCatalog {

  ToolProvider getToolProvider(String toolName);

  ToolsetProvider getToolsetProvider(String toolsetName);
}

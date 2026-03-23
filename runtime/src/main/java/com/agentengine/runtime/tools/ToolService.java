package com.agentengine.runtime.tools;

import com.agentengine.runtime.api.services.ToolCatalog;

public interface ToolService extends ToolCatalog {

  ToolProvider getToolProvider(String toolName);

  ToolsetProvider getToolsetProvider(String toolsetName);
}

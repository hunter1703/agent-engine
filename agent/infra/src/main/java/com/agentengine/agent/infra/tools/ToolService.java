package com.agentengine.agent.infra.tools;

import com.agentengine.agent.api.services.ToolCatalog;

public interface ToolService extends ToolCatalog {

  ToolProvider getToolProvider(String toolName);

  ToolsetProvider getToolsetProvider(String toolsetName);
}

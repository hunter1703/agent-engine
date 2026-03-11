package com.agentengine.engine.api.services;

import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolsetProvider;
import com.agentengine.util.ms.MicroService;
import com.agentengine.engine.api.tools.ToolDescriptor;
import java.util.List;

@MicroService("agent")
public interface ToolCatalog {
  List<ToolDescriptor> getTools();

  ToolDescriptor getToolByName(String toolName);

  ToolProvider getToolProvider(String toolName);

  ToolsetProvider getToolsetProvider(String toolsetName);
}

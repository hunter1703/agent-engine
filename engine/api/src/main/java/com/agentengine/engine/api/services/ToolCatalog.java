package com.agentengine.engine.api.services;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.util.ms.MicroService;
import java.util.List;

@MicroService("agent")
public interface ToolCatalog {
  List<ToolDescriptor> getTools();

  ToolDescriptor getToolByName(String toolName);
}

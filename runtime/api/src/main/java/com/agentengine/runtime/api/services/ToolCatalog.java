package com.agentengine.runtime.api.services;

import com.agentengine.util.agents.tools.ToolDescriptor;
import com.agentengine.util.ms.MicroService;
import java.util.List;

@MicroService("agent")
public interface ToolCatalog {
  List<ToolDescriptor> getTools();

  ToolDescriptor getToolByName(String toolName);
}

package com.agentengine.runtime.api.services;

import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.ms.MicroService;
import java.util.List;

@MicroService("runtime")
public interface ToolCatalog {
  List<ToolDescriptor> getTools();

  ToolDescriptor getToolByName(String toolName);
}

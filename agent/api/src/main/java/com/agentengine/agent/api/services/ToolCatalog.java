package com.agentengine.agent.api.services;

import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.ms.client.MicroService;
import java.util.List;

@MicroService("agent")
public interface ToolCatalog {
  List<ToolDescriptor> getTools();

  ToolDescriptor getToolByName(String toolName);

  List<ToolDescriptor> getStandardTools();
}

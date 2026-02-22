package com.agentengine.engine.api.services;

import com.agentengine.engine.api.MicroService;
import com.agentengine.engine.api.tools.ToolDescriptor;

import java.util.List;

@MicroService
public interface ToolService {
  List<ToolDescriptor> getAvailableTools(String agentId);

  ToolDescriptor getToolById(String agentId, String toolId);
}

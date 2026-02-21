package com.agentengine.engine.api.services;

import com.agentengine.engine.api.MicroService;
import com.agentengine.engine.api.beans.ToolEntity;
import java.util.List;

@MicroService
public interface ToolService {
  List<ToolEntity> getAvailableTools(String agentId);

  ToolEntity getToolById(String agentId, String toolId);
}

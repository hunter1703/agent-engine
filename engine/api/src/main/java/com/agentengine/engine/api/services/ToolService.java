package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.ToolEntity;
import com.agentengine.engine.api.MicroService;
import java.util.List;

@MicroService
public interface ToolService {
  List<ToolEntity> getAvailableTools(String agentId);
}

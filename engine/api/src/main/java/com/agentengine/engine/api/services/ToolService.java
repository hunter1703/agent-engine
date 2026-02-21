package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.ToolEntity;
import com.agentengine.engine.api.MicroService;

import java.util.List;
import java.util.Map;

@MicroService
public interface ToolService {
    List<ToolEntity> getAvailableTools(String agentId);

    ToolEntity getToolById(String agentId, String toolId);
}

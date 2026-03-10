package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.ms.MicroService;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import java.util.Optional;

@MicroService
public interface AgentService {
  PaginatedResult<BaseAgentConfig> findAgents(Query query);

  Optional<BaseAgentConfig> getAgent(String id);

  BaseAgentConfig createAgent(BaseAgentConfig agent);

  BaseAgentConfig saveAgent(BaseAgentConfig agent);

  BaseAgentConfig updateAgent(String id, BaseAgentConfig agent);

  boolean deleteAgent(String id);
}

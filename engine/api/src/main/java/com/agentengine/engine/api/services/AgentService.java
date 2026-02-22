package com.agentengine.engine.api.services;

import com.agentengine.engine.api.MicroService;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.utils.PaginatedResult;
import java.util.Optional;

@MicroService
public interface AgentService {
  PaginatedResult<AgentConfig> findAgents(Query query);

  Optional<AgentConfig> getAgent(String id);

  AgentConfig createAgent(AgentConfig agent);

  AgentConfig saveAgent(AgentConfig agent);

  AgentConfig updateAgent(AgentConfig agent);

  boolean deleteAgent(String id);
}

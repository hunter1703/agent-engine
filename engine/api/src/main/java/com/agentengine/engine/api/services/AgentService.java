package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.utils.PaginatedResult;
import com.agentengine.engine.api.query.Query;

import java.util.Optional;

import com.agentengine.engine.api.MicroService;

@MicroService
public interface AgentService {
  PaginatedResult<AgentConfig> findAgents(Query query);

  Optional<AgentConfig> getAgent(String id);

  AgentConfig createAgent(AgentConfig agent);

  AgentConfig updateAgent(AgentConfig agent);

  boolean deleteAgent(String id);
}

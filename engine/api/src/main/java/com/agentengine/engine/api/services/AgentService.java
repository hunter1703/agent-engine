package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.ms.MicroService;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@MicroService
public interface AgentService {
  PaginatedResult<BaseAgentConfig> findAgents(Query query);

  Optional<BaseAgentConfig> getAgent(String id);

  Map<String, BaseAgentConfig> getAgents(Collection<String> ids);

  BaseAgentConfig createAgent(BaseAgentConfig agent);

  BaseAgentConfig saveAgent(BaseAgentConfig agent);

  BaseAgentConfig updateAgent(String id, BaseAgentConfig agent);

  boolean deleteAgent(String id);
}

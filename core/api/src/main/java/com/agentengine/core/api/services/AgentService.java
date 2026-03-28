package com.agentengine.core.api.services;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.MicroService;
import java.util.Collection;
import java.util.Map;

@MicroService("agent")
public interface AgentService {
  PaginatedResult<BaseAgentConfig> findAgents(Query query);

  BaseAgentConfig getAgent(String id);

  Map<String, BaseAgentConfig> getAgents(Collection<String> ids);

  BaseAgentConfig createAgent(BaseAgentConfig agent);

  BaseAgentConfig saveAgent(BaseAgentConfig agent);

  BaseAgentConfig updateAgent(String id, BaseAgentConfig agent);

  boolean deleteAgent(String id);
}

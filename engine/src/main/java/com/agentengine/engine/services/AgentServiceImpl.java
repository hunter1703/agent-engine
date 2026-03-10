package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.repository.AgentRepository;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

@Singleton
@Unremovable
public class AgentServiceImpl implements AgentService {

  @Inject AgentRepository agentRepository;

  @Override
  @WithSpan
  public PaginatedResult<BaseAgentConfig> findAgents(Query query) {
    return agentRepository.findByQuery(query);
  }

  @Override
  @WithSpan
  public Optional<BaseAgentConfig> getAgent(String id) {
    return agentRepository.findById(id);
  }

  @Override
  @WithSpan
  public BaseAgentConfig createAgent(final BaseAgentConfig agent) {
    return agentRepository.insert(agent);
  }

  @Override
  @WithSpan
  public BaseAgentConfig saveAgent(BaseAgentConfig agent) {
    return agentRepository.save(agent);
  }

  @Override
  @WithSpan
  public BaseAgentConfig updateAgent(final String id, final BaseAgentConfig agent) {
    return agentRepository.update(id, agent);
  }

  @Override
  @WithSpan
  public boolean deleteAgent(String id) {
    return agentRepository.deleteById(id);
  }
}

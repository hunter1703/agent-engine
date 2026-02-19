package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.utils.PaginatedResult;

import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.repository.AgentRepository;

import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
@Unremovable
public class AgentServiceImpl implements AgentService {

  @Inject
  AgentRepository agentRepository;

  @Override
  @WithSpan
  public PaginatedResult<AgentConfig> findAgents(Query query) {
    return agentRepository.findByQuery(query);
  }

  @Override
  @WithSpan
  public Optional<AgentConfig> getAgent(String id) {
    return agentRepository.findById(id);
  }

  @Override
  @WithSpan
  public AgentConfig createAgent(AgentConfig agent) {
    return agentRepository.insert(agent);
  }

  @Override
  @WithSpan
  public AgentConfig updateAgent(AgentConfig agent) {
    return agentRepository.update(agent.getId(), agent);
  }

  @Override
  @WithSpan
  public boolean deleteAgent(String id) {
    return agentRepository.deleteById(id);
  }
}

package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.utils.PaginatedResult;

import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.repository.AgentRepository;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;

import java.util.Optional;

@Singleton
public class AgentServiceImpl implements AgentService {

    @Inject
    AgentRepository agentRepository;

    @Override
    public PaginatedResult<AgentConfig> findAgents(Query query) {
        return agentRepository.findByQuery(query);
    }

    @Override
    public Optional<AgentConfig> getAgent(String id) {
        return agentRepository.findById(id);
    }

    @Override
    public AgentConfig createAgent(AgentConfig agent) {
        return agentRepository.insert(agent);
    }

    @Override
    public AgentConfig updateAgent(AgentConfig agent) {
        return agentRepository.update(agent.getId(), agent);
    }

    @Override
    public boolean deleteAgent(String id) {
        return agentRepository.deleteById(id);
    }
}

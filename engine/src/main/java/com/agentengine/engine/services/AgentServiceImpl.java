package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.builder.BuilderDefinition;
import com.agentengine.util.common.builder.BuilderDefinitionUtils;
import com.agentengine.util.common.builder.BuilderMode;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Singleton
@Unremovable
public class AgentServiceImpl implements AgentService {

  private static final BuilderDefinition AGENT_DEFINITION =
      BuilderDefinitionUtils.generate(BaseAgentConfig.class);

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
  public Map<String, BaseAgentConfig> getAgents(final Collection<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Map.of();
    }
    return agentRepository.findByIds(ids);
  }

  @Override
  @WithSpan
  public BaseAgentConfig createAgent(final BaseAgentConfig agent) {
    return agentRepository.insert(sanitize(agent, BuilderMode.CREATE));
  }

  @Override
  @WithSpan
  public BaseAgentConfig saveAgent(final BaseAgentConfig agent) {
    final BuilderMode mode =
        StringUtils.isBlank(agent == null ? null : agent.getId())
            ? BuilderMode.CREATE
            : BuilderMode.EDIT;
    return agentRepository.save(sanitize(agent, mode));
  }

  @Override
  @WithSpan
  public BaseAgentConfig updateAgent(final String id, final BaseAgentConfig agent) {
    return agentRepository.update(id, sanitize(agent, BuilderMode.EDIT));
  }

  @Override
  @WithSpan
  public boolean deleteAgent(String id) {
    return agentRepository.deleteById(id);
  }

  private static BaseAgentConfig sanitize(final BaseAgentConfig config, final BuilderMode mode) {
    return BuilderDefinitionUtils.sanitize(AGENT_DEFINITION, mode, config);
  }
}

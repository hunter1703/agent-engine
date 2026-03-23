package com.agentengine.core.services;

import com.agentengine.core.api.services.AgentService;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.GuardrailRule;
import com.agentengine.engine.api.beans.config.GuardrailsConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.runtime.api.repository.AgentRepository;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.GuardrailRule;
import com.agentengine.util.agents.beans.config.GuardrailsConfig;
import com.agentengine.util.common.CollectionUtils;
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

import java.util.*;

@Singleton
@Unremovable
public class AgentServiceImpl implements AgentService {

  private static final BuilderDefinition AGENT_DEFINITION = BuilderDefinitionUtils.generate(BaseAgentConfig.class);

  private final AgentRepository agentRepository;

  @Inject
  public AgentServiceImpl(final AgentRepository agentRepository) {
    this.agentRepository = agentRepository;
  }

  @Override
  @WithSpan
  public PaginatedResult<BaseAgentConfig> findAgents(Query query) {
    return agentRepository.findByQuery(query);
  }

  @Override
  @WithSpan
  public BaseAgentConfig getAgent(String id) {
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
    // Preserve caller-supplied ID if provided, otherwise let Mongo generate one
    final String id = agent == null ? null : agent.getId();
    final BaseAgentConfig sanitized = sanitizeConfig(id, agent, BuilderMode.CREATE);
    return agentRepository.insert(sanitized);
  }

  @Override
  @WithSpan
  public BaseAgentConfig saveAgent(final BaseAgentConfig agent) {
    final String id = agent == null ? null : agent.getId();
    final BaseAgentConfig sanitized = sanitizeConfig(id, agent, StringUtils.isBlank(id) ? BuilderMode.CREATE : BuilderMode.EDIT);
    return agentRepository.save(sanitized);
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

  private static BaseAgentConfig sanitizeConfig(final String id, final BaseAgentConfig agent, final BuilderMode mode) {
    final BaseAgentConfig sanitized = sanitize(agent, mode);
    if (StringUtils.isNotBlank(id)) {
      sanitized.setId(id);
    }
    final GuardrailsConfig guardrails = sanitized.getGuardrails();
    final List<GuardrailRule> rules = CollectionUtils.nullSafeList(guardrails == null ? null : guardrails.getRules());
    rules.forEach(rule -> {
      if (StringUtils.isBlank(rule.getId())) {
        rule.setId(UUID.randomUUID().toString());
      }
    });
    return sanitized;
  }
}

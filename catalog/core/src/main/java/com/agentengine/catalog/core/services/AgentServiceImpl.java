package com.agentengine.catalog.core.services;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.core.repository.AgentRepository;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.GuardrailRule;
import com.agentengine.util.agents.beans.config.GuardrailsConfig;
import com.agentengine.util.agents.builder.BuilderDefinition;
import com.agentengine.util.agents.builder.BuilderDefinitionUtils;
import com.agentengine.util.agents.builder.BuilderMode;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tenancy.Permission;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
@Unremovable
public class AgentServiceImpl implements AgentService {

  private static final BuilderDefinition AGENT_DEFINITION =
      BuilderDefinitionUtils.generate(BaseAgentConfig.class);

  private final AgentRepository agentRepository;
  private final DistributedCacheManager cacheManager;

  @Inject
  public AgentServiceImpl(
      final AgentRepository agentRepository, final DistributedCacheManager cacheManager) {
    this.agentRepository = agentRepository;
    this.cacheManager = cacheManager;
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
    final String id = agent == null ? null : agent.getId();
    final BaseAgentConfig sanitized = sanitizeConfig(id, agent, BuilderMode.CREATE);
    requireSubAgentsExist(sanitized);
    requireCanShareAddedSubAgents(id, sanitized);
    return agentRepository.insert(sanitized);
  }

  @Override
  @WithSpan
  public BaseAgentConfig saveAgent(final BaseAgentConfig agent, final boolean skipVersion) {
    if (agent == null) {
      throw new IllegalArgumentException("Agent should be non-null");
    }
    final String id = agent.getId();
    final boolean isEdit = StringUtils.isNotBlank(id);
    final BaseAgentConfig sanitized =
        sanitizeConfig(id, agent, isEdit ? BuilderMode.EDIT : BuilderMode.CREATE);
    requireSubAgentsExist(sanitized);
    requireCanShareAddedSubAgents(id, sanitized);
    final BaseAgentConfig saved =
        skipVersion
            ? agentRepository.saveIgnoringVersion(sanitized)
            : agentRepository.save(sanitized);
    if (isEdit) {
      invalidateCachedRunners(id);
    }
    return saved;
  }

  @Override
  @WithSpan
  public BaseAgentConfig updateAgent(final String id, final BaseAgentConfig agent) {
    final BaseAgentConfig sanitized = sanitize(agent, BuilderMode.EDIT);
    requireSubAgentsExist(sanitized);
    requireCanShareAddedSubAgents(id, sanitized);
    final BaseAgentConfig updated = agentRepository.update(id, sanitized);
    invalidateCachedRunners(id);
    return updated;
  }

  @Override
  @WithSpan
  public boolean deleteAgent(String id) {
    return agentRepository.deleteByIdIgnoringVersion(id);
  }

  /**
   * Listing an agent as a sub-agent hands its use to whoever may run the listing agent, so it takes
   * SHARE on each sub-agent added — no one hands out an agent they may only use.
   */
  private void requireCanShareAddedSubAgents(final String id, final BaseAgentConfig agent) {
    final Set<String> added = subAgentIds(agent);
    if (StringUtils.isNotBlank(id)) {
      added.removeAll(subAgentIds(agentRepository.findById(id)));
    }
    final Set<String> shareable = agentRepository.findPermittedIds(added, Permission.SHARE);
    for (final String subAgentId : added) {
      if (!shareable.contains(subAgentId)) {
        throw new UnauthorizedException(AssetClass.AGENT, subAgentId);
      }
    }
  }

  private void requireSubAgentsExist(final BaseAgentConfig agent) {
    final Set<String> ids = subAgentIds(agent);
    if (ids.isEmpty()) {
      return;
    }
    final Map<String, BaseAgentConfig> subAgents = agentRepository.findByIds(ids);
    final List<String> missing = ids.stream().filter(id -> !subAgents.containsKey(id)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Sub-agent(s) not found: " + String.join(", ", missing));
    }
  }

  /**
   * Drops the cached runners of the agent and of every agent that lists it as a sub-agent, however
   * far up, since their runners were built with the sub-agent's old config. A runner already
   * running keeps what it was built with; the next request builds a new one.
   */
  private void invalidateCachedRunners(final String agentId) {
    final Set<String> invalidated = new HashSet<>();
    final Deque<String> pending = new ArrayDeque<>(List.of(agentId));
    while (!pending.isEmpty()) {
      final String id = pending.poll();
      if (invalidated.add(id)) {
        cacheManager.invalidateByPrefix(RuntimeService.RUNNER_CACHE, id + ID_SEPARATOR);
        cacheManager.invalidate(RuntimeService.TEMP_RUNNER_CACHE, id);
        pending.addAll(findIdsOfAgentsListing(id));
      }
    }
  }

  /**
   * The ids of the agents that list {@code subAgentId} as a sub-agent, whoever may read them: whose
   * runners to drop is not the caller's to know.
   */
  private List<String> findIdsOfAgentsListing(final String subAgentId) {
    final Query listing =
        new Query()
            .withFilter(
                Filters.or(
                    Filters.eq(BaseAgentConfig.FIELD_SUB_AGENT_IDS, subAgentId),
                    Filters.eq(BaseAgentConfig.FIELD_TRANSFERABLE_SUB_AGENT_IDS, subAgentId)))
            .withPage(Page.UNBOUNDED);
    return Context.require()
        .asSystemCaller()
        .get(
            () ->
                agentRepository.findByQuery(listing).getItems().stream()
                    .map(BaseAgentConfig::getId)
                    .toList());
  }

  private static Set<String> subAgentIds(final BaseAgentConfig config) {
    final Set<String> ids = new LinkedHashSet<>();
    if (config != null) {
      ids.addAll(CollectionUtils.nullSafeList(config.getSubAgentIds()));
      ids.addAll(CollectionUtils.nullSafeList(config.getTransferableSubAgentIds()));
      ids.removeIf(StringUtils::isBlank);
    }
    return ids;
  }

  private static BaseAgentConfig sanitize(final BaseAgentConfig config, final BuilderMode mode) {
    return BuilderDefinitionUtils.sanitize(AGENT_DEFINITION, mode, config);
  }

  private static BaseAgentConfig sanitizeConfig(
      final String id, final BaseAgentConfig agent, final BuilderMode mode) {
    final BaseAgentConfig sanitized = sanitize(agent, mode);
    if (StringUtils.isNotBlank(id)) {
      sanitized.setId(id);
    }
    final GuardrailsConfig guardrails = sanitized.getGuardrails();
    final List<GuardrailRule> rules =
        CollectionUtils.nullSafeList(guardrails == null ? null : guardrails.getRules());
    rules.forEach(
        rule -> {
          if (StringUtils.isBlank(rule.getId())) {
            rule.setId(UUID.randomUUID().toString());
          }
        });
    return sanitized;
  }
}

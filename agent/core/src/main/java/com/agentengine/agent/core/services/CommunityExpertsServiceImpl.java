package com.agentengine.agent.core.services;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.CommunityExpertsService;
import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.factories.agent.AgentProvider;
import com.agentengine.agent.infra.plugins.InitPlugin;
import com.agentengine.agent.infra.plugins.ResponseValidationPlugin;
import com.agentengine.agent.infra.utils.AgentUtils;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.*;
import com.agentengine.util.context.Context;
import com.google.adk.apps.App;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.common.cache.CacheBuilder;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads expert agent definitions bundled as classpath resources under {@code
 * agent/core/src/main/resources/agents/community/experts}.
 *
 * <p>Each expert config file is read as a BaseAgentConfig. Experts are regular agent configs with
 * optional capabilities metadata for discovery.
 */
@Singleton
public final class CommunityExpertsServiceImpl implements CommunityExpertsService {

  private static final Logger LOG = LoggerFactory.getLogger(CommunityExpertsServiceImpl.class);
  private static final String EXPERTS_INDEX = "agents/community/experts/.index";
  private static final int MAX_RESOLVED_CONFIGS = 200;

  private final LazyLoader<List<BaseAgentConfig>> experts;
  private final AgentProvider agentProvider;

  /**
   * A local, per-instance cache — not the distributed model cache {@code ModelProvider} uses —
   * since these configs are read from bundled classpath resources: they never change without a
   * redeploy, so there's nothing for another instance's write to invalidate here. Keyed by (expert
   * id, modelId) rather than mutating the single shared {@link BaseAgentConfig} {@link #getExpert}
   * returns for that expert: two concurrent {@link #invokeExpert} calls for the same expert with
   * different {@code modelId}s would otherwise race on that one shared instance's {@code modelId}
   * field, each able to corrupt the other's (and every later caller's) request.
   */
  private final Cache<ExpertKey, BaseAgentConfig> resolvedConfigs;

  public CommunityExpertsServiceImpl(final AgentProvider agentProvider) {
    this.agentProvider = agentProvider;
    this.experts = new LazyLoader<>(this::loadExperts);
    this.resolvedConfigs =
        new Cache<>(
            CacheBuilder.newBuilder().maximumSize(MAX_RESOLVED_CONFIGS), this::resolveConfig);
  }

  @Override
  public List<BaseAgentConfig> findExperts(final String query) {
    return experts.get();
  }

  @Override
  public BaseAgentConfig getExpert(final String id) {
    return experts.get().stream().filter(c -> id.equals(c.getId())).findFirst().orElse(null);
  }

  @Override
  public String invokeExpert(final String id, final String modelId, final UserMessage userMessage) {
    final BaseAgentConfig config = resolvedConfigs.get(new ExpertKey(id, modelId));
    try {
      final Agent agent = agentProvider.create(config);
      final InMemorySessionService sessionService = new InMemorySessionService();
      final String sessionId = UUID.randomUUID().toString();
      final String appName = AgentUtils.appName(config.getId());
      final String userId = String.valueOf(Context.userId().orElse(-1));
      sessionService
          .createSession(appName, userId, new ConcurrentHashMap<>(), sessionId)
          .blockingGet();
      final Runner runner =
          Runner.builder()
              .app(
                  App.builder()
                      .plugins(List.of(new InitPlugin(null), new ResponseValidationPlugin()))
                      .rootAgent(agent)
                      .name(appName)
                      .build())
              .sessionService(sessionService)
              .build();
      final Content prompt = Content.fromParts(toParts(userMessage));
      final String responseText =
          runner
              .runAsync(userId, sessionId, prompt)
              .filter(event -> !userId.equalsIgnoreCase(event.author()))
              .filter(event -> !event.partial().orElse(false))
              .filter(event -> event.content().isPresent())
              .map(event -> event.content().orElseThrow().text())
              .filter(StringUtils::isNotBlank)
              .lastElement()
              .blockingGet();
      agent.close();
      return responseText;
    } catch (final Exception e) {
      throw ExceptionUtils.wrapInRuntimeException(e);
    }
  }

  private List<BaseAgentConfig> loadExperts() {
    final List<BaseAgentConfig> result = new ArrayList<>();
    final ResourceIndex index = new ResourceIndex(EXPERTS_INDEX);
    for (final String resourceName : index.listEntries()) {
      final String content = index.findContent(resourceName).orElse(null);
      if (StringUtils.isBlank(content)) {
        continue;
      }
      try {
        final BaseAgentConfig config = JsonUtils.fromJson(content, BaseAgentConfig.class);
        result.add(config);
        LOG.info("Loaded expert: {} ({})", config.getName(), config.getId());
      } catch (final Exception exception) {
        LOG.error("Failed to load expert from {}", resourceName, exception);
      }
    }
    LOG.info("Loaded {} expert(s) from {}", result.size(), EXPERTS_INDEX);
    return result;
  }

  /**
   * Builds the config {@code key} resolves to: the expert's own config unchanged when no {@code
   * modelId} override applies, otherwise a private copy (a JSON round-trip — {@link
   * BaseAgentConfig} has no copy constructor) with the override applied, so the copy that goes on
   * to be cached is never the same instance {@link #getExpert} hands out for that expert.
   */
  private BaseAgentConfig resolveConfig(final ExpertKey key) {
    final BaseAgentConfig base = getExpert(key.expertId());
    if (base == null) {
      throw new IllegalArgumentException("No such expert with id " + key.expertId());
    }
    if (StringUtils.isBlank(key.modelId())) {
      return base;
    }
    final BaseAgentConfig resolved =
        JsonUtils.fromJson(JsonUtils.toJson(base), BaseAgentConfig.class);
    resolved.setModelId(key.modelId());
    return resolved;
  }

  private record ExpertKey(String expertId, String modelId) {}

  private static Part[] toParts(final UserMessage userMessage) {
    final List<Part> parts = new ArrayList<>();
    final StringBuilder text = new StringBuilder();
    for (final MessagePart part : userMessage.parts()) {
      switch (part) {
        case MessagePart.TextPart textPart -> {
          if (!text.isEmpty()) {
            text.append('\n');
          }
          text.append(textPart.text());
        }
        case MessagePart.BinaryPart binaryPart ->
            parts.add(Part.fromBytes(binaryPart.bytes(), binaryPart.mimeType()));
        default -> throw new IllegalArgumentException("Unsupported message part: " + part);
      }
    }
    if (!text.isEmpty()) {
      parts.addFirst(Part.fromText(text.toString()));
    }
    return parts.toArray(new Part[0]);
  }
}

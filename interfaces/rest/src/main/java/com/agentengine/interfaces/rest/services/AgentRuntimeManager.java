package com.agentengine.interfaces.rest.services;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ConfigLoader;
import com.agentengine.engine.api.utils.HashUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.builders.state.ToolAwareSessionService;
import com.google.adk.agents.LlmAgent;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import jakarta.inject.Singleton;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AgentRuntimeManager {
  private static final Logger LOG = LoggerFactory.getLogger(AgentRuntimeManager.class);
  public static final String DEFAULT_USER_ID = "default";

  private final AgentProvider agentProvider;
  private final ConfigLoader configLoader;
  private final ConfigRepository configRepository;
  private final SessionServiceProvider sessionServiceProvider;
  private final Map<String, AgentRuntime> runtimes = new ConcurrentHashMap<>();

  public AgentRuntimeManager(final AgentProvider agentProvider, final ConfigLoader configLoader,
      final ConfigRepository configRepository, final SessionServiceProvider sessionServiceProvider) {
    this.agentProvider = agentProvider;
    this.configLoader = configLoader;
    this.configRepository = configRepository;
    this.sessionServiceProvider = sessionServiceProvider;
  }

  public AgentRuntime getOrStartRuntime(final String agentId, final String configPath) {
    if (StringUtils.isBlank(agentId)) {
      LOG.warn("Agent ID validation failed - agent_id=\"\" config_path=\"{}\"", configPath);
      throw new IllegalArgumentException("agentId is required");
    }

    LOG.debug("Retrieving or starting agent engine - agent_id={} config_path=\"{}\"", agentId, configPath);

    final AgentConfig agentConfig = resolveAgentConfig(agentId, configPath);
    if (agentConfig == null) {
      String errorMsg = STR."agentId \"\{agentId}\" has no resolved config";
      LOG.error("Agent configuration resolution failed - agent_id={} config_path=\"{}\" error=\"{}\"",
                agentId, configPath, errorMsg);
      throw new IllegalArgumentException(errorMsg);
    }

    agentConfig.validate();
    final String key = HashUtils.HMACSHA256_Base64(STR."\{agentId}|\{JsonUtils.toStableJson(agentConfig)}");

    AgentRuntime runtime = runtimes.computeIfAbsent(key, k -> {
      final String prefix = key.substring(0, Math.min(8, key.length()));
      LOG.info("Creating new agent runtime - agent_id={} config_hash={} operation=agent.create", agentId, prefix);
      LOG.debug("Creating new agent runtime - agent_id={} config_name=\"{}\" config_hash={}", agentId,
          agentConfig.getAgentId(), prefix);
      return createRuntime(agentConfig);
    });

    LOG.debug("Agent runtime retrieved - agent_id={} cached={}", agentId, runtimes.containsKey(key));
    return runtime;
  }

  private AgentRuntime createRuntime(final AgentConfig agentConfig) {
    final BaseSessionService sessionService =
        new ToolAwareSessionService(sessionServiceProvider.get(agentConfig.getSessionStore()));
    final AgentContext agentContext = new AgentContext(agentConfig, sessionService);
    final LlmAgent agent = agentProvider.get(agentConfig, agentContext);
    final Runner runner = Runner.builder().agent(agent).appName(agentConfig.getAgentId()).sessionService(sessionService)
        .build();
    return new AgentRuntime(agent, runner, sessionService, agentConfig.getAgentId());
  }

  private AgentConfig resolveAgentConfig(final String agentId, final String configPath) {
    AgentConfig config;
    if (StringUtils.isNotBlank(configPath)) {
      LOG.debug("Loading agent configuration from file - agent_id={} config_path={}", agentId, configPath);
      config = configLoader.loadConfig(Paths.get(configPath));
    } else {
      LOG.debug("Loading agent configuration from repository - agent_id={}", agentId);
      config = configRepository.loadAgentConfig(agentId);
    }

    if (config == null) {
      LOG.warn("Agent configuration not found - agent_id={} config_path=\"{}\"", agentId, configPath);
    } else {
      LOG.debug("Agent configuration loaded - agent_id={} config_name=\"{}\"", agentId, config.getAgentId());
    }

    return config;
  }
}

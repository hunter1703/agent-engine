package com.agentengine.interfaces.rest.services;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.utils.LoggingUtils;
import com.agentengine.engine.api.utils.HashUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ConfigLoader;
import com.agentengine.engine.builders.agent.AgentProvider;
import jakarta.inject.Singleton;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AgentManager {
  private static final Logger LOG = LoggerFactory.getLogger(AgentManager.class);

  private final AgentProvider agentProvider;
  private final ConfigLoader configLoader;
  private final ConfigRepository configRepository;
  private final Map<String, Agent> engines = new ConcurrentHashMap<>();

  public AgentManager(final AgentProvider agentProvider, final ConfigLoader configLoader,
      final ConfigRepository configRepository) {
    this.agentProvider = agentProvider;
    this.configLoader = configLoader;
    this.configRepository = configRepository;
  }

  public Agent getOrStartEngine(final String agentId, final String configPath) {
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

    Agent agent = engines.computeIfAbsent(key, k -> {
      LOG.info("Creating new agent instance - agent_id={} config_hash={} operation=agent.create", agentId, key.substring(0, Math.min(8, key.length())));
      return agentProvider.get(agentConfig);
    });

    LOG.debug("Agent engine retrieved - agent_id={} cached={}", agentId, engines.containsKey(key));
    return agent;
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
      LOG.debug("Agent configuration loaded - agent_id={} config_name=\"{}\"", agentId, config.getName());
    }

    return config;
  }
}

package com.agentengine.interfaces.rest.services;

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

@Singleton
public class AgentManager {
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
      throw new IllegalArgumentException("agentId is required");
    }
    final AgentConfig agentConfig = resolveAgentConfig(agentId, configPath);
    if (agentConfig == null) {
      throw new IllegalArgumentException(STR."agentId \"\{agentId}\" has no resolved config");
    }
    agentConfig.validate();
    final String key = HashUtils.HMACSHA256_Base64(STR."\{agentId}|\{JsonUtils.toStableJson(agentConfig)}");
    return engines.computeIfAbsent(
        key,
        ignored -> agentProvider.get(agentConfig));
  }

  private AgentConfig resolveAgentConfig(final String agentId, final String configPath) {
    if (StringUtils.isNotBlank(configPath)) {
      return configLoader.loadConfig(Paths.get(configPath));
    }
    return configRepository.loadAgentConfig(agentId);
  }
}

package com.agentengine.interfaces.rest.services;

import com.agentengine.commons.utils.JsonUtils;
import com.agentengine.commons.utils.StringUtils;
import com.agentengine.commons.utils.HashUtils;
import com.agentengine.engine.client.AgentEngine;
import com.agentengine.engine.client.ConfigRepository;
import com.agentengine.engine.client.beans.config.AgentConfig;
import com.agentengine.engine.client.beans.config.ConfigLoader;
import com.agentengine.engine.client.builders.AgentBuilderFactory;
import com.agui.core.exception.AGUIException;
import com.agui.server.LocalAgent;
import jakarta.inject.Singleton;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AgentManager {
  private final AgentBuilderFactory builderFactory;
  private final ConfigLoader configLoader;
  private final ConfigRepository configRepository;
  private final Map<String, AGUIAgent> engines = new ConcurrentHashMap<>();

  public AgentManager(final AgentBuilderFactory builderFactory, final ConfigLoader configLoader,
      final ConfigRepository configRepository) {
    this.builderFactory = builderFactory;
    this.configLoader = configLoader;
    this.configRepository = configRepository;
  }

  public AGUIAgent getOrStartEngine(final String agentName, final String configPath) {
    if (StringUtils.isBlank(agentName)) {
      throw new IllegalArgumentException("agentName is required");
    }
    final AgentConfig agentConfig = resolveAgentConfig(agentName, configPath);
    if (agentConfig == null) {
      throw new IllegalArgumentException(STR."agentName \"\{agentName}\" has no resolved config");
    }
    agentConfig.validate();
    final String key = HashUtils.HMACSHA256_Base64(STR."\{agentName}|\{JsonUtils.toStableJson(agentConfig)}");
    return engines.computeIfAbsent(
        key,
        ignored -> {
          final AgentEngine engine = builderFactory.getBuilder(agentConfig.getEngine().getType()).build(agentName, agentConfig);
          try {
            return new AGUIAgent(key, engine);
          } catch (AGUIException e) {
            throw new RuntimeException(STR."Failed to create AGUIAgent for agentName \"\{agentName}\"", e);
          }
        });
  }

  private AgentConfig resolveAgentConfig(final String agentName, final String configPath) {
    if (StringUtils.isNotBlank(configPath)) {
      return configLoader.loadConfig(Paths.get(configPath));
    }
    return configRepository.loadAgentConfig(agentName);
  }
}

package com.agentengine.interfaces;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.EngineConfig;
import com.agentengine.engine.beans.config.ConfigLoader;
import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.builders.AgentBuilderFactory;
import com.agentengine.engine.utils.JsonUtils;
import com.agentengine.engine.utils.StringUtils;
import com.agentengine.interfaces.common.utils.HashUtils;
import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AgentService {
  private final AgentBuilderFactory builderFactory;
  private final ConfigLoader configLoader;
  private final ConfigRepository configRepository;
  private final Map<String, AgentEngine> engines = new ConcurrentHashMap<>();

  public AgentService(final AgentBuilderFactory builderFactory, final ConfigLoader configLoader,
      final ConfigRepository configRepository) {
    this.builderFactory = builderFactory;
    this.configLoader = configLoader;
    this.configRepository = configRepository;
  }

  public AgentEngine getOrStartEngine(final String agentName, final String configPath) {
    final AgentConfig agentConfig = resolveAgentConfig(agentName, configPath);
    agentConfig.validate();
    final String key = HashUtils.HMACSHA256_Base64(STR."\{agentName}|\{JsonUtils.toJson(agentConfig)}");
    return engines.computeIfAbsent(
        key,
        ignored -> builderFactory.getBuilder(agentConfig.getEngine().getType()).build(agentName, agentConfig));
  }

  private AgentConfig resolveAgentConfig(final String agentName, final String configPath) {
    if (StringUtils.isNotBlank(configPath)) {
      return configLoader.loadConfig(Paths.get(configPath));
    }
    return configRepository.loadAgentConfig(agentName);
  }
}

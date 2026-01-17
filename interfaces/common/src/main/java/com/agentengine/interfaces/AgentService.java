package com.agentengine.interfaces;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ConfigLoader;
import com.agentengine.engine.builders.AgentBuilderFactory;
import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AgentService {
  private static final String PLUGIN_DIR_PROPERTY = "PLUGIN_DIR";
  private static final String DEFAULT_CONFIG_DIR = "plugins/config";
  private final AgentBuilderFactory builderFactory;
  private final ConfigLoader configLoader;
  private final Map<String, AgentEngine> engines = new ConcurrentHashMap<>();

  public AgentService(final AgentBuilderFactory builderFactory, final ConfigLoader configLoader) {
    this.builderFactory = builderFactory;
    this.configLoader = configLoader;
  }

  public AgentEngine getOrStartEngine(final String agentName, final String configPath) {
    final String resolvedName = resolveAgentName(agentName);
    final String resolvedConfigPath = resolveConfigPath(resolvedName, configPath);
    final String key = STR."\{resolvedName}|\{resolvedConfigPath}";
    return engines.computeIfAbsent(
        key,
        ignored -> {
          AgentConfig config = configLoader.loadConfig(Paths.get(resolvedConfigPath));
          return builderFactory.getBuilder(resolvedName).build(resolvedName, config);
        });
  }

  private static String resolveAgentName(final String agentName) {
    if (agentName != null && !agentName.isBlank()) {
      return agentName;
    }
    String fromEnv = System.getenv("AGENT_NAME");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    throw new IllegalArgumentException("agentName is required");
  }

  private static String resolveConfigPath(final String agentName, final String configPath) {
    if (configPath != null && !configPath.isBlank()) {
      return configPath;
    }
    String fromEnv = System.getenv("AGENT_CONFIG_PATH");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    Path baseDir = resolvePluginConfigDir();
    String baseName = agentName.toLowerCase(Locale.ROOT);
    Path jsonPath = baseDir.resolve(STR."\{baseName}.json");
    if (Files.exists(jsonPath)) {
      return jsonPath.toString();
    }
    Path yamlPath = baseDir.resolve(STR."\{baseName}.yaml");
    if (Files.exists(yamlPath)) {
      return yamlPath.toString();
    }
    Path ymlPath = baseDir.resolve(STR."\{baseName}.yml");
    if (Files.exists(ymlPath)) {
      return ymlPath.toString();
    }
    return null;
  }

  private static Path resolvePluginConfigDir() {
    String fromProperty = System.getProperty(PLUGIN_DIR_PROPERTY);
    if (fromProperty != null && !fromProperty.isBlank()) {
      return Paths.get(fromProperty, "config");
    }
    String fromEnv = System.getenv(PLUGIN_DIR_PROPERTY);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return Paths.get(fromEnv, "config");
    }
    return Paths.get(DEFAULT_CONFIG_DIR);
  }
}

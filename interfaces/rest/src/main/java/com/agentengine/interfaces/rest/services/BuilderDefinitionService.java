package com.agentengine.interfaces.rest.services;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.Cache;
import com.agentengine.util.common.builder.BuilderDefinition;
import com.agentengine.util.common.builder.BuilderDefinitionUtils;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Singleton;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@Singleton
public class BuilderDefinitionService {

  private final Cache<String, BuilderDefinition> definitions = new Cache<>(CacheBuilder.newBuilder(), this::generateDefinition);

  public BuilderDefinition getDefinition(final String assetType) {
    return definitions.get(assetType);
  }

  private BuilderDefinition generateDefinition(final String assetType) {
    return switch (assetType) {
      case "agent" -> BuilderDefinitionUtils.generate(BaseAgentConfig.class);
      case "model" -> BuilderDefinitionUtils.generate(ModelConfig.class);
      default -> throw new IllegalArgumentException("Unsupported assetType: " + assetType);
    };
  }
}

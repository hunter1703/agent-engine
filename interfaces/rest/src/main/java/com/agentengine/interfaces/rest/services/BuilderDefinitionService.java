package com.agentengine.interfaces.rest.services;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.Cache;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.builder.BuilderDefinition;
import com.agentengine.util.common.builder.BuilderDefinitionUtils;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Singleton;

@Singleton
public class BuilderDefinitionService {

  private final Cache<String, BuilderDefinition> definitions =
      new Cache<>(CacheBuilder.newBuilder(), this::generateDefinition);

  public BuilderDefinition getDefinition(final String assetType) {
    return definitions.get(assetType);
  }

  private BuilderDefinition generateDefinition(final String assetType) {
    return switch (assetType) {
      case AssetClass.AGENT -> BuilderDefinitionUtils.generate(BaseAgentConfig.class);
      case AssetClass.MODEL -> BuilderDefinitionUtils.generate(ModelConfig.class);
      default -> throw new IllegalArgumentException("Unsupported assetType: " + assetType);
    };
  }
}

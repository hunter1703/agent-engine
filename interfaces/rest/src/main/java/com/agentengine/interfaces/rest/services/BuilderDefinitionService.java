package com.agentengine.interfaces.rest.services;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Singleton;
import com.agentengine.util.builder.BuilderDefinition;
import com.agentengine.util.builder.BuilderDefinitionUtils;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@Singleton
public class BuilderDefinitionService {

  private final Cache<String, BuilderDefinition> definitions = CacheBuilder.newBuilder().build();

  public BuilderDefinition getDefinition(final String assetType) {
    final String normalizedAssetType = normalizeAssetType(assetType);
    try {
      return definitions.get(normalizedAssetType, () -> generateDefinition(normalizedAssetType));
    } catch (ExecutionException exception) {
      throw new IllegalStateException(
          "Failed to build definition for assetType: " + normalizedAssetType, exception);
    }
  }

  private BuilderDefinition generateDefinition(final String assetType) {
    return switch (assetType) {
      case "agent" -> BuilderDefinitionUtils.generate(BaseAgentConfig.class);
      case "model" -> BuilderDefinitionUtils.generate(ModelConfig.class);
      default -> throw new IllegalArgumentException("Unsupported assetType: " + assetType);
    };
  }

  private static String normalizeAssetType(final String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}

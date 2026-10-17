package com.agentengine.interfaces.rest.services;

import com.agentengine.util.agents.builder.BuilderDefinition;
import com.agentengine.util.agents.builder.BuilderDefinitionUtils;
import com.agentengine.util.common.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class BuilderDefinitionService {

  private final Map<String, Class<?>> assetClassVsDtoClass;
  private final Cache<String, BuilderDefinition> definitions =
      new Cache<>(CacheBuilder.newBuilder(), this::generateDefinition);

  @Inject
  public BuilderDefinitionService(final Instance<SchemaDTOProvider> providers) {
    this.assetClassVsDtoClass =
        providers.stream()
            .flatMap(provider -> provider.assetClasses().entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public BuilderDefinition getDefinition(final String assetType) {
    return definitions.get(assetType);
  }

  private BuilderDefinition generateDefinition(final String assetType) {
    final Class<?> dtoClass = assetClassVsDtoClass.get(assetType);
    if (dtoClass == null) {
      throw new IllegalArgumentException("Unsupported assetType: " + assetType);
    }
    return BuilderDefinitionUtils.generate(dtoClass);
  }
}

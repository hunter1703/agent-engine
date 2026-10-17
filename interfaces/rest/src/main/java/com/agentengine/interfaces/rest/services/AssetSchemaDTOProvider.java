package com.agentengine.interfaces.rest.services;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.models.config.ModelConfig;
import jakarta.inject.Singleton;
import java.util.Map;

/** This module's own schema-resolvable asset types. */
@Singleton
public class AssetSchemaDTOProvider implements SchemaDTOProvider {

  @Override
  public Map<String, Class<?>> assetClasses() {
    return Map.of(AssetClass.AGENT, BaseAgentConfig.class, AssetClass.MODEL, ModelConfig.class);
  }
}

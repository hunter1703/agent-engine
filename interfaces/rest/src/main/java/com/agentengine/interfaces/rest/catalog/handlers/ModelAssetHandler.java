package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.services.ModelService;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.catalog.NamedAssetHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class ModelAssetHandler extends NamedAssetHandler<ModelConfig> {

  private static final String ASSET_TYPE = "model";

  private final ModelService modelService;

  @Inject
  public ModelAssetHandler(ModelService modelService) {
    this.modelService = modelService;
  }

  @Override
  public String getAssetType() {
    return ASSET_TYPE;
  }

  @Override
  public PaginatedResult<ModelConfig> findAssets(AssetRequest request) {
    return modelService.findModels(request.getQuery());
  }

  @Override
  public Map<String, ModelConfig> getAssetsByIds(AssetRequest request) {
    Map<String, ModelConfig> result = new HashMap<>();
    if (request.getKeys() == null || request.getKeys().isEmpty()) {
      return result;
    }

    for (String key : request.getKeys()) {
      modelService.getModel(key).ifPresent(value -> result.put(key, value));
    }

    return result;
  }
}

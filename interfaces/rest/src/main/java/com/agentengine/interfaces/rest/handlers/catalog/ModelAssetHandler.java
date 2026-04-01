package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.core.api.services.ModelService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.query.PaginatedResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class ModelAssetHandler extends NamedAssetHandler<ModelConfig> {

    private final ModelService modelService;

    @Inject
    public ModelAssetHandler(ModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public String getAssetType() {
        return AssetClass.MODEL;
    }

    @Override
    public PaginatedResult<ModelConfig> findAssets(AssetRequest request) {
        return modelService.findModels(request.getQuery());
    }

    @Override
    public Map<String, ModelConfig> getAssetsByIds(AssetRequest request) {
        return modelService.getModels(request.getKeys());
    }
}

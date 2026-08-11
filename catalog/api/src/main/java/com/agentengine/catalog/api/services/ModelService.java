package com.agentengine.catalog.api.services;

import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.client.MicroService;
import java.util.Collection;
import java.util.Map;

@MicroService("catalog")
public interface ModelService {
    PaginatedResult<ModelConfig> findModels(Query query);

    ModelConfig getModel(String id);

    Map<String, ModelConfig> getModels(Collection<String> ids);

    ModelConfig createModel(ModelConfig model);

    ModelConfig saveModel(ModelConfig model);

    ModelConfig updateModel(String id, ModelConfig model);

    boolean deleteModel(String id);
}

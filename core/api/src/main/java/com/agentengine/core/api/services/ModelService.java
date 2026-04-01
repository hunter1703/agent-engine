package com.agentengine.core.api.services;

import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.MicroService;
import java.util.Collection;
import java.util.Map;

@MicroService("agent")
public interface ModelService {
    PaginatedResult<ModelConfig> findModels(Query query);

    ModelConfig getModel(String id);

    Map<String, ModelConfig> getModels(Collection<String> ids);

    ModelConfig createModel(ModelConfig model);

    ModelConfig saveModel(ModelConfig model);

    ModelConfig updateModel(String id, ModelConfig model);

    boolean deleteModel(String id);
}

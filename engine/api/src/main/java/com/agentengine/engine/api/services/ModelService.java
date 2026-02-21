package com.agentengine.engine.api.services;

import com.agentengine.engine.api.MicroService;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.utils.PaginatedResult;
import java.util.Optional;

@MicroService
public interface ModelService {
  PaginatedResult<ModelConfig> findModels(Query query);

  Optional<ModelConfig> getModel(String id);

  ModelConfig createModel(ModelConfig model);

  ModelConfig updateModel(ModelConfig model);

  boolean deleteModel(String id);
}

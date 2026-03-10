package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.ms.MicroService;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@MicroService
public interface ModelService {
  PaginatedResult<ModelConfig> findModels(Query query);

  Optional<ModelConfig> getModel(String id);
  Map<String, ModelConfig> getModels(Collection<String> ids);

  ModelConfig createModel(ModelConfig model);

  ModelConfig saveModel(ModelConfig model);

  ModelConfig updateModel(String id, ModelConfig model);

  boolean deleteModel(String id);
}

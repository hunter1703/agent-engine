package com.agentengine.catalog.api.services;

import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.models.ModelLookup;
import com.agentengine.util.models.config.ModelConfig;
import com.agentengine.util.ms.client.MicroService;
import java.util.Collection;
import java.util.Map;

@MicroService("catalog")
public interface ModelService extends ModelLookup {

  PaginatedResult<ModelConfig> findModels(Query query);

  @Override
  ModelConfig getModel(String id);

  Map<String, ModelConfig> getModels(Collection<String> ids);

  ModelConfig createModel(ModelConfig model);

  ModelConfig saveModel(ModelConfig model, boolean skipVersion);

  ModelConfig updateModel(String id, ModelConfig model);

  boolean deleteModel(String id);
}

package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.services.ModelService;
import com.agentengine.engine.api.utils.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.repository.ModelRepository;

import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
@Unremovable
public class ModelServiceImpl implements ModelService {

  @Inject
  ModelRepository modelRepository;

  @Override
  public PaginatedResult<ModelConfig> findModels(Query query) {
    return modelRepository.findByQuery(query);
  }

  @Override
  public Optional<ModelConfig> getModel(String id) {
    return modelRepository.findById(id);
  }

  @Override
  public ModelConfig createModel(ModelConfig model) {
    return modelRepository.insert(model);
  }

  @Override
  public ModelConfig updateModel(ModelConfig model) {
    return modelRepository.update(model.getId(), model);
  }

  @Override
  public boolean deleteModel(String id) {
    return modelRepository.deleteById(id);
  }
}

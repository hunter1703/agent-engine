package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.services.ModelService;
import com.agentengine.engine.repository.ModelRepository;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

@Singleton
@Unremovable
public class ModelServiceImpl implements ModelService {

  @Inject ModelRepository modelRepository;

  @Override
  @WithSpan
  public PaginatedResult<ModelConfig> findModels(Query query) {
    return modelRepository.findByQuery(query);
  }

  @Override
  @WithSpan
  public Optional<ModelConfig> getModel(String id) {
    return modelRepository.findById(id);
  }

  @Override
  @WithSpan
  public ModelConfig createModel(final ModelConfig model) {
    return modelRepository.insert(model);
  }

  @Override
  @WithSpan
  public ModelConfig saveModel(ModelConfig model) {
    return modelRepository.save(model);
  }

  @Override
  @WithSpan
  public ModelConfig updateModel(final String id, final ModelConfig model) {
    return modelRepository.update(id, model);
  }

  @Override
  @WithSpan
  public boolean deleteModel(String id) {
    return modelRepository.deleteById(id);
  }
}

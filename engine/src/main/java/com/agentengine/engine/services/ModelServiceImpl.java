package com.agentengine.engine.services;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.util.query.PaginatedResult;
import com.agentengine.util.query.Query;
import com.agentengine.engine.api.services.ModelService;
import com.agentengine.util.StringUtils;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.util.builder.BuilderDefinition;
import com.agentengine.util.builder.BuilderDefinitionUtils;
import com.agentengine.util.builder.BuilderMode;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Singleton
@Unremovable
public class ModelServiceImpl implements ModelService {

  private static final BuilderDefinition MODEL_DEFINITION =
      BuilderDefinitionUtils.generate(ModelConfig.class);

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
  public Map<String, ModelConfig> getModels(final Collection<String> ids) {
    return modelRepository.findByIds(ids);
  }

  @Override
  @WithSpan
  public ModelConfig createModel(final ModelConfig model) {
    return modelRepository.insert(sanitize(model, BuilderMode.CREATE));
  }

  @Override
  @WithSpan
  public ModelConfig saveModel(final ModelConfig model) {
    final BuilderMode mode =
        StringUtils.isBlank(model == null ? null : model.getId())
            ? BuilderMode.CREATE
            : BuilderMode.EDIT;
    return modelRepository.save(sanitize(model, mode));
  }

  @Override
  @WithSpan
  public ModelConfig updateModel(final String id, final ModelConfig model) {
    return modelRepository.update(id, sanitize(model, BuilderMode.EDIT));
  }

  @Override
  @WithSpan
  public boolean deleteModel(String id) {
    return modelRepository.deleteById(id);
  }

  private static ModelConfig sanitize(final ModelConfig config, final BuilderMode mode) {
    return BuilderDefinitionUtils.sanitize(MODEL_DEFINITION, mode, config);
  }
}

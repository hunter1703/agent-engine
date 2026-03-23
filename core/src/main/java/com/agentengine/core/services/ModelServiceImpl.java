package com.agentengine.core.services;

import com.agentengine.core.api.services.ModelService;
import com.agentengine.core.repository.ModelRepository;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.builder.BuilderDefinition;
import com.agentengine.util.common.builder.BuilderDefinitionUtils;
import com.agentengine.util.common.builder.BuilderMode;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
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

  private static final BuilderDefinition MODEL_DEFINITION = BuilderDefinitionUtils.generate(ModelConfig.class);

  private final ModelRepository modelRepository;

  @Inject
  public ModelServiceImpl(final ModelRepository modelRepository) {
    this.modelRepository = modelRepository;
  }

  @Override
  @WithSpan
  public PaginatedResult<ModelConfig> findModels(Query query) {
    return modelRepository.findByQuery(query);
  }

  @Override
  @WithSpan
  public ModelConfig getModel(String id) {
    return modelRepository.findById(id);
  }

  @Override
  public Map<String, ModelConfig> getModels(final Collection<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Map.of();
    }
    return modelRepository.findByIds(ids);
  }

  @Override
  @WithSpan
  public ModelConfig createModel(final ModelConfig model) {
    // Preserve caller-supplied ID if provided, otherwise let Mongo generate one
    final String id = model == null ? null : model.getId();
    final ModelConfig sanitized = sanitize(model, BuilderMode.CREATE);
    if (StringUtils.isNotBlank(id)) {
      sanitized.setId(id);
    }
    return modelRepository.insert(sanitized);
  }

  @Override
  @WithSpan
  public ModelConfig saveModel(final ModelConfig model) {
    final String id = model == null ? null : model.getId();
    final BuilderMode mode = StringUtils.isBlank(id) ? BuilderMode.CREATE : BuilderMode.EDIT;
    final ModelConfig sanitized = sanitize(model, mode);
    if (mode == BuilderMode.EDIT) {
      sanitized.setId(id);
    }
    return modelRepository.save(sanitized);
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

package com.agentengine.engine.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelServiceImplTest {

  @Mock private ModelRepository modelRepository;

  @InjectMocks private ModelServiceImpl modelService;

  @Test
  void shouldSanitizeBeforeInsertWhenCreateModelCalled() {
    final ModelConfig request = new ModelConfig();
    request.setId("model-1");
    request.setType("OLLAMA");
    request.setModel("qwen2.5");
    when(modelRepository.insert(any(ModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    final ModelConfig created = modelService.createModel(request);

    assertThat(created.getId()).isNull();
    verify(modelRepository).insert(any(ModelConfig.class));
  }

  @Test
  void shouldDelegateFindModelsWhenFindModelsCalled() {
    final Query query = new Query();
    final PaginatedResult<ModelConfig> expected = PaginatedResult.create(java.util.List.of());
    when(modelRepository.findByQuery(query)).thenReturn(expected);

    final PaginatedResult<ModelConfig> result = modelService.findModels(query);

    assertThat(result).isSameAs(expected);
    verify(modelRepository).findByQuery(query);
  }

  @Test
  void shouldDelegateGetModelWhenGetModelCalled() {
    final ModelConfig config = new ModelConfig();
    config.setId("model-1");
    when(modelRepository.findById("model-1")).thenReturn(Optional.of(config));

    final Optional<ModelConfig> result = modelService.getModel("model-1");

    assertThat(result).contains(config);
    verify(modelRepository).findById("model-1");
  }

  @Test
  void shouldDelegateDeleteModelWhenDeleteModelCalled() {
    when(modelRepository.deleteById("model-1")).thenReturn(true);

    final boolean deleted = modelService.deleteModel("model-1");

    assertThat(deleted).isTrue();
    verify(modelRepository).deleteById("model-1");
  }

  @Test
  void shouldDelegateUpdateModelWhenUpdateModelCalled() {
    final ModelConfig request = new ModelConfig();
    request.setId("model-2");
    request.setType("OLLAMA");
    request.setModel("qwen2.5");
    when(modelRepository.update(eq("model-1"), any(ModelConfig.class)))
        .thenAnswer(inv -> inv.getArgument(1));

    final ModelConfig updated = modelService.updateModel("model-1", request);

    assertThat(updated.getId()).isNull();
    verify(modelRepository).update(eq("model-1"), any(ModelConfig.class));
  }

  @Test
  void shouldSanitizeBeforeSaveWhenSaveModelCalled() {
    final ModelConfig request = new ModelConfig();
    request.setId("model-3");
    request.setType("OLLAMA");
    request.setModel("qwen2.5");
    when(modelRepository.save(any(ModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    final ModelConfig saved = modelService.saveModel(request);

    assertThat(saved.getId()).isNull();
    verify(modelRepository).save(any(ModelConfig.class));
  }
}

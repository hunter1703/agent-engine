package com.agentengine.engine.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.repository.ModelRepository;
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
  void shouldDelegateCreateModelWhenCreateModelCalled() {
    final ModelConfig request = new ModelConfig();
    when(modelRepository.insert(request)).thenReturn(request);

    final ModelConfig created = modelService.createModel(request);

    assertThat(created).isSameAs(request);
    verify(modelRepository).insert(request);
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
    when(modelRepository.update("model-1", request)).thenReturn(request);

    final ModelConfig updated = modelService.updateModel("model-1", request);

    assertThat(updated).isSameAs(request);
    verify(modelRepository).update("model-1", request);
  }
}

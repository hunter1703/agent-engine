package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.services.ModelService;
import jakarta.ws.rs.WebApplicationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelRestAPITest {

  @Test
  void shouldThrowBadRequestWhenGetModelCalledWithBlankId() {
    final ModelRestAPI api = new ModelRestAPI(mock(ModelService.class));

    assertThatThrownBy(() -> api.getModel(" "))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowNotFoundWhenModelMissing() {
    final ModelService modelService = mock(ModelService.class);
    when(modelService.getModel("model-1")).thenReturn(Optional.empty());
    final ModelRestAPI api = new ModelRestAPI(modelService);

    assertThatThrownBy(() -> api.getModel("model-1"))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(404);
  }

  @Test
  void shouldDelegateCreateModelWhenCreateModelCalled() {
    final ModelService modelService = mock(ModelService.class);
    final ModelRestAPI api = new ModelRestAPI(modelService);
    final ModelConfig model = new ModelConfig();
    model.setType("ollama");
    model.setModel("qwen2.5");
    when(modelService.createModel(any(ModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    final ModelConfig created = api.createModel(model);

    assertThat(created).isNotNull();
    assertThat(created.getId()).isNull();
    verify(modelService).createModel(any(ModelConfig.class));
  }

  @Test
  void shouldDelegateCreateModelValidationToService() {
    final ModelService modelService = mock(ModelService.class);
    when(modelService.createModel(any(ModelConfig.class)))
        .thenThrow(new IllegalArgumentException("Config validation failed"));
    final ModelRestAPI api = new ModelRestAPI(modelService);
    final ModelConfig model = new ModelConfig();

    assertThatThrownBy(() -> api.createModel(model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Config validation failed");
    verify(modelService).createModel(any(ModelConfig.class));
  }

  @Test
  void shouldThrowBadRequestWhenUpdateModelCalledWithNullConfig() {
    final ModelRestAPI api = new ModelRestAPI(mock(ModelService.class));

    assertThatThrownBy(() -> api.updateModel("model-1", null))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldThrowBadRequestWhenUpdateModelIdMismatch() {
    final ModelService modelService = mock(ModelService.class);
    final ModelRestAPI api = new ModelRestAPI(modelService);
    final ModelConfig model = new ModelConfig();
    model.setId("model-2");
    model.setType("ollama");
    model.setModel("qwen2.5");

    assertThatThrownBy(() -> api.updateModel("model-1", model))
        .isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  void shouldDelegateUpdateModelWhenUpdateModelCalled() {
    final ModelService modelService = mock(ModelService.class);
    final ModelRestAPI api = new ModelRestAPI(modelService);
    final ModelConfig model = new ModelConfig();
    model.setType("ollama");
    model.setModel("qwen2.5");
    when(modelService.updateModel(eq("model-1"), any(ModelConfig.class)))
        .thenAnswer(inv -> inv.getArgument(1));

    final ModelConfig updated = api.updateModel("model-1", model);

    assertThat(updated).isNotNull();
    assertThat(updated.getId()).isNull();
    verify(modelService).updateModel(eq("model-1"), any(ModelConfig.class));
  }

  @Test
  void shouldNotCallGetModelBeforeCreateModel() {
    final ModelService svc = mock(ModelService.class);
    final ModelConfig config = new ModelConfig();
    config.setType("ollama");
    config.setModel("qwen2.5");
    when(svc.createModel(any())).thenReturn(config);
    new ModelRestAPI(svc).createModel(config);
    verify(svc, never()).getModel(any());
  }

  @Test
  void shouldDelegateDeleteModelWhenDeleteModelCalled() {
    final ModelService modelService = mock(ModelService.class);
    final ModelRestAPI api = new ModelRestAPI(modelService);

    api.deleteModel("model-1");

    verify(modelService).deleteModel("model-1");
  }
}

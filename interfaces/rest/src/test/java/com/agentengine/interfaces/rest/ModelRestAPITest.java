package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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

    final ModelConfig created = api.createModel(model);

    assertThat(created).isSameAs(model);
    verify(modelService).createModel(model);
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
  void shouldDelegateDeleteModelWhenDeleteModelCalled() {
    final ModelService modelService = mock(ModelService.class);
    final ModelRestAPI api = new ModelRestAPI(modelService);

    api.deleteModel("model-1");

    verify(modelService).deleteModel("model-1");
  }
}

package com.agentengine.interfaces.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.interfaces.rest.ResourceCatalogAPI;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.interfaces.rest.handlers.catalog.AssetHandler;
import com.agentengine.interfaces.rest.handlers.catalog.NamedAssetHandler;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class ResourceCatalogAPITest {

  @Test
  void shouldThrowBadRequestWhenListResourcesMissingAssetType() {
    final ResourceCatalogAPI api = new ResourceCatalogAPI(handlerInstanceWith(new NamedModelHandler()));

    assertThatThrownBy(() -> api.listResources(new AssetRequest())).isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  void shouldThrowBadRequestWhenListResourcesHandlerUnsupported() {
    final ResourceCatalogAPI api = new ResourceCatalogAPI(handlerInstanceWith(new NamedModelHandler()));
    final AssetRequest request = new AssetRequest();
    request.setAssetType("unknown");

    assertThatThrownBy(() -> api.listResources(request)).isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  void shouldThrowBadRequestWhenListResourcesHandlerNotNamed() {
    final ResourceCatalogAPI api = new ResourceCatalogAPI(handlerInstanceWith(new PlainModelHandler()));
    final AssetRequest request = new AssetRequest();
    request.setAssetType("model");

    assertThatThrownBy(() -> api.listResources(request)).isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  void shouldReturnResourcesWhenListResourcesHandlerIsNamed() {
    final ResourceCatalogAPI api = new ResourceCatalogAPI(handlerInstanceWith(new NamedModelHandler()));
    final AssetRequest request = new AssetRequest();
    request.setAssetType("model");
    request.setQuery(new Query().withPage(new Page(0, 10)));

    final PaginatedResult<? extends NamedEntity> result = api.listResources(request);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().getFirst().getId()).isEqualTo("model-1");
    assertThat(result.getItems().getFirst().getName()).isEqualTo("Model One");
  }

  @Test
  void shouldReturnResourceWhenGetResourceCalledWithKnownId() {
    final ResourceCatalogAPI api = new ResourceCatalogAPI(handlerInstanceWith(new NamedModelHandler()));

    final ModelConfig resource = api.getResource("model", "model-1", null);

    assertThat(resource.getId()).isEqualTo("model-1");
  }

  @Test
  void shouldThrowNotFoundWhenGetResourceCalledWithUnknownId() {
    final ResourceCatalogAPI api = new ResourceCatalogAPI(handlerInstanceWith(new NamedModelHandler()));

    assertThatThrownBy(() -> api.getResource("model", "missing", null)).isInstanceOf(WebApplicationException.class)
        .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(404);
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  private static Instance<AssetHandler<?>> handlerInstanceWith(final AssetHandler<?>... handlers) {
    final Instance<AssetHandler<?>> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(java.util.stream.Stream.of(handlers));
    return instance;
  }

  private static final class PlainModelHandler implements AssetHandler<ModelConfig> {

    @Override
    public String getAssetType() {
      return "model";
    }

    @Override
    public PaginatedResult<ModelConfig> findAssets(final AssetRequest request) {
      return PaginatedResult.create(java.util.List.of(newModel("model-1", "Model One")));
    }

    @Override
    public java.util.Map<String, ModelConfig> getAssetsByIds(final AssetRequest request) {
      return java.util.Map.of("model-1", newModel("model-1", "Model One"));
    }
  }

  private static final class NamedModelHandler extends NamedAssetHandler<ModelConfig> {

    @Override
    public String getAssetType() {
      return "model";
    }

    @Override
    public PaginatedResult<ModelConfig> findAssets(final AssetRequest request) {
      return PaginatedResult.create(java.util.List.of(newModel("model-1", "Model One")));
    }

    @Override
    public java.util.Map<String, ModelConfig> getAssetsByIds(final AssetRequest request) {
      if (request.getKeys() == null || request.getKeys().isEmpty()) {
        return java.util.Map.of();
      }
      final String key = request.getKeys().getFirst();
      if (!"model-1".equals(key)) {
        return java.util.Map.of();
      }
      return java.util.Map.of("model-1", newModel("model-1", "Model One"));
    }
  }

  private static ModelConfig newModel(final String id, final String name) {
    final ModelConfig config = new ModelConfig();
    config.setId(id);
    config.setName(name);
    config.setType("OLLAMA");
    config.setModel("qwen");
    return config;
  }
}

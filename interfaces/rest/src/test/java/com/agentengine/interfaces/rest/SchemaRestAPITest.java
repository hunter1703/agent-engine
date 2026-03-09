package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.interfaces.rest.handlers.SchemaRequestHandler;
import com.agentengine.interfaces.rest.requests.SchemaLookupRequest;
import com.agentengine.interfaces.rest.services.ResourceService;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaRestAPITest {

  @Test
  void shouldReturnSchemaWhenGetSchemaCalledForKnownAssetType() throws IOException {
    final ResourceService resourceService = mock(ResourceService.class);
    when(resourceService.getJsonResource("model")).thenReturn(Map.of("type", "object"));

    final SchemaRestAPI api = new SchemaRestAPI(resourceService, handlerInstanceWith());
    final Response response = api.getSchema("model");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(Map.of("type", "object"));
  }

  @Test
  void shouldReturnNotFoundWhenSchemaMissing() throws IOException {
    final ResourceService resourceService = mock(ResourceService.class);
    when(resourceService.getJsonResource("missing")).thenThrow(new IOException("not found"));

    final SchemaRestAPI api = new SchemaRestAPI(resourceService, handlerInstanceWith());
    final Response response = api.getSchema("missing");

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity().toString()).contains("not found");
  }

  @Test
  void shouldReturnBadRequestWhenResolveSchemaCalledForUnsupportedAssetType() {
    final SchemaRestAPI api =
        new SchemaRestAPI(mock(ResourceService.class), handlerInstanceWith(new ToolConfigsHandler()));

    final Response response = api.resolveSchema(new SchemaLookupRequest("unsupported", "id-1", "agent-1"));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity().toString()).contains("Unsupported assetType");
  }

  @Test
  void shouldReturnResolvedSchemaWhenHandlerRegistered() {
    final SchemaRestAPI api =
        new SchemaRestAPI(mock(ResourceService.class), handlerInstanceWith(new ToolConfigsHandler()));

    final Response response = api.resolveSchema(new SchemaLookupRequest("tool_configs", "id-1", "agent-1"));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(Map.of("schema", "resolved"));
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  private static Instance<SchemaRequestHandler> handlerInstanceWith(
      final SchemaRequestHandler... handlers) {
    final Instance<SchemaRequestHandler> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(java.util.stream.Stream.of(handlers));
    return instance;
  }

  private static final class ToolConfigsHandler implements SchemaRequestHandler {

    @Override
    public String getAssetType() {
      return "tool_configs";
    }

    @Override
    public Object handle(final SchemaLookupRequest request) {
      return Map.of("schema", "resolved");
    }
  }
}

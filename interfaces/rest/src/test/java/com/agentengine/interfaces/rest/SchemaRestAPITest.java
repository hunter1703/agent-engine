package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.interfaces.rest.dto.SchemaLookupRequest;
import com.agentengine.interfaces.rest.handlers.SchemaRequestHandler;
import com.agentengine.interfaces.rest.services.BuilderDefinitionService;
import com.agentengine.util.common.builder.BuilderDefinition;
import com.agentengine.util.common.builder.UILayout;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SchemaRestAPITest {

  @Test
  void shouldReturnSchemaWhenGetSchemaCalledForKnownAssetType() {
    final BuilderDefinitionService definitionService = mock(BuilderDefinitionService.class);
    when(definitionService.getDefinition("model"))
        .thenReturn(new BuilderDefinition(Map.of("type", "object"), new UILayout(Map.of(), null)));

    final SchemaRestAPI api = new SchemaRestAPI(definitionService, handlerInstanceWith());
    final Response response = api.getSchema("model", null);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(BuilderDefinition.class);
    assertThat(((BuilderDefinition) response.getEntity()).schema()).isEqualTo(Map.of("type", "object"));
  }

  @Test
  void shouldReturnNotFoundWhenSchemaMissing() {
    final BuilderDefinitionService definitionService = mock(BuilderDefinitionService.class);
    when(definitionService.getDefinition("missing")).thenThrow(new IllegalArgumentException("not found"));

    final SchemaRestAPI api = new SchemaRestAPI(definitionService, handlerInstanceWith());
    final Response response = api.getSchema("missing", null);

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity().toString()).contains("not found");
  }

  @Test
  void shouldReturnBadRequestWhenSchemaModeIsUnsupported() {
    final SchemaRestAPI api = new SchemaRestAPI(mock(BuilderDefinitionService.class), handlerInstanceWith());

    final Response response = api.getSchema("model", "unsupported");

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity().toString()).contains("Unsupported mode");
  }

  @Test
  void shouldReturnBadRequestWhenResolveSchemaCalledForUnsupportedAssetType() {
    final SchemaRestAPI api = new SchemaRestAPI(mock(BuilderDefinitionService.class), handlerInstanceWith(new ToolConfigsHandler()));

    final Response response = api.resolveSchema(new SchemaLookupRequest("unsupported", "id-1", "agent-1"));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity().toString()).contains("Unsupported assetType");
  }

  @Test
  void shouldReturnResolvedSchemaWhenHandlerRegistered() {
    final SchemaRestAPI api = new SchemaRestAPI(mock(BuilderDefinitionService.class), handlerInstanceWith(new ToolConfigsHandler()));

    final Response response = api.resolveSchema(new SchemaLookupRequest("tool_configs", "id-1", "agent-1"));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(Map.of("schema", "resolved"));
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  private static Instance<SchemaRequestHandler> handlerInstanceWith(final SchemaRequestHandler... handlers) {
    final Instance<SchemaRequestHandler> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of(handlers));
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

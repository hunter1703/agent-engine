package com.agentengine.engine.tools.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.connectors.core.ConnectorService;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebLookupToolTest {

  @Test
  void throwsForBlankQuery() {
    final WebLookupTool tool = new WebLookupTool(new StubConnectorService(success("unused")));
    assertThatThrownBy(() -> tool.execute("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Query cannot be empty");
  }

  @Test
  void returnsMappedResultWhenConnectorSucceeds() {
    final WebLookupTool tool =
        new WebLookupTool(new StubConnectorService(success("Richard Feynman")));
    final Map<String, Object> result = tool.execute("richard feynman");

    assertThat(result).containsEntry("result", "Richard Feynman");
  }

  @Test
  void returnsFallbackMessageWhenMappedResultIsEmpty() {
    final WebLookupTool tool = new WebLookupTool(new StubConnectorService(success("   ")));
    final Map<String, Object> result = tool.execute("query");

    assertThat(result).containsEntry("result", "No abstract available for this query.");
  }

  @Test
  void returnsErrorMessageWhenConnectorFails() {
    final WebLookupTool tool =
        new WebLookupTool(new StubConnectorService(failure("Connector unavailable")));
    final Map<String, Object> result = tool.execute("query");

    assertThat(result).containsEntry("error", "Connector unavailable");
  }

  private static ConnectorExecutionResult success(final Object data) {
    return new ConnectorExecutionResult(
        200, true, "https://example.com", "GET", Map.of(), data, null, null, null, null, false);
  }

  private static ConnectorExecutionResult failure(final String message) {
    return new ConnectorExecutionResult(
        503,
        false,
        "https://example.com",
        "GET",
        Map.of(),
        null,
        null,
        null,
        "SERVICE_UNAVAILABLE",
        message,
        true);
  }

  private record StubConnectorService(ConnectorExecutionResult response)
      implements ConnectorService {
    @Override
    public ConnectorExecutionResult execute(
        final String connectorId, final Map<String, Object> input) {
      assertThat(connectorId).isEqualTo("duckduckgo_instant_search");
      assertThat(input).containsKey("query");
      return response;
    }
  }
}

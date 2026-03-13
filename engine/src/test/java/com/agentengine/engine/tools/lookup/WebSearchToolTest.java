package com.agentengine.engine.tools.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.connectors.core.ConnectorService;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebSearchToolTest {

  @Test
  void throwsForBlankQuery() {
    final WebSearchTool tool = new WebSearchTool(new StubConnectorService(success(Map.of())));
    assertThatThrownBy(() -> tool.execute(" ", null, null, null)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Query cannot be empty");
  }

  @Test
  void usesDefaultsWhenOptionalArgumentsAreMissing() {
    final CapturingConnectorService connector = new CapturingConnectorService(success(Map.of("ok", true)));
    final WebSearchTool tool = new WebSearchTool(connector);

    final Map<String, Object> result = tool.execute("mediterranean sea", null, null, null);

    assertThat(result).containsEntry("result", Map.of("ok", true));
    assertThat(connector.lastConnectorId).isEqualTo("brave_web_search");
    assertThat(connector.lastInput).containsEntry("query", "mediterranean sea").containsEntry("country", "US")
        .containsEntry("search_lang", "en").containsEntry("maximum_number_of_tokens", 8192);
  }

  @Test
  void forwardsExplicitArguments() {
    final CapturingConnectorService connector = new CapturingConnectorService(success(Map.of("ok", true)));
    final WebSearchTool tool = new WebSearchTool(connector);

    tool.execute("query", "AE", "ar", 4096);

    assertThat(connector.lastInput).containsEntry("query", "query").containsEntry("country", "AE").containsEntry("search_lang", "ar")
        .containsEntry("maximum_number_of_tokens", 4096);
  }

  @Test
  void returnsErrorWhenConnectorFails() {
    final WebSearchTool tool = new WebSearchTool(new StubConnectorService(failure("brave search unavailable")));

    final Map<String, Object> result = tool.execute("query", null, null, null);

    assertThat(result).containsEntry("error", "brave search unavailable");
  }

  private static ConnectorExecutionResult success(final Object data) {
    return new ConnectorExecutionResult(200, true, "https://example.com", "POST", Map.of(), data, null, null, null, null, false);
  }

  private static ConnectorExecutionResult failure(final String message) {
    return new ConnectorExecutionResult(503, false, "https://example.com", "POST", Map.of(), null, null, null, "BRAVE_DOWN", message, true);
  }

  private record StubConnectorService(ConnectorExecutionResult response) implements ConnectorService {
    @Override
    public ConnectorExecutionResult execute(final String connectorId, final Map<String, Object> input) {
      return response;
    }
  }

  private static final class CapturingConnectorService implements ConnectorService {
    private final ConnectorExecutionResult response;
    private String lastConnectorId;
    private Map<String, Object> lastInput;

    private CapturingConnectorService(final ConnectorExecutionResult response) {
      this.response = response;
    }

    @Override
    public ConnectorExecutionResult execute(final String connectorId, final Map<String, Object> input) {
      this.lastConnectorId = connectorId;
      this.lastInput = input;
      return response;
    }
  }
}

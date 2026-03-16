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
    assertThatThrownBy(() -> tool.execute(" ", null, null, null, null)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Query cannot be empty");
  }

  @Test
  void usesDefaultsWhenOptionalArgumentsAreMissing() {
    final Map<String, Object> duckDuckGoResponse = Map.of("abstract", "Quick summary result", "url", "https://example.com", "source",
        "Example", "heading", "Result");
    final CapturingConnectorService connector = new CapturingConnectorService(success(duckDuckGoResponse));
    final WebSearchTool tool = new WebSearchTool(connector);

    final Map<String, Object> result = tool.execute("mediterranean sea", null, null, null, null);

    assertThat(result).containsEntry("result", duckDuckGoResponse);
    assertThat(connector.lastConnectorId).isEqualTo("duckduckgo_instant_search");
    assertThat(connector.lastInput).containsEntry("query", "mediterranean sea");
  }

  @Test
  void forwardsExplicitArguments() {
    final CapturingConnectorService connector = new CapturingConnectorService(success(Map.of("ok", true)));
    final WebSearchTool tool = new WebSearchTool(connector);

    tool.execute("query", "AE", "ar", 4096, true);

    assertThat(connector.lastConnectorId).isEqualTo("brave_web_search");
    assertThat(connector.lastInput).containsEntry("query", "query").containsEntry("country", "AE").containsEntry("search_lang", "ar")
        .containsEntry("maximum_number_of_tokens", 4096);
  }

  @Test
  void returnsErrorWhenConnectorFails() {
    final WebSearchTool tool = new WebSearchTool(new StubConnectorService(failure("duckduckgo unavailable")));

    final Map<String, Object> result = tool.execute("query", null, null, null, null);

    assertThat(result).containsEntry("error", "duckduckgo unavailable");
  }

  @Test
  void usesDuckDuckGoWhenDetailedIsFalse() {
    final Map<String, Object> duckDuckGoResponse = Map.of("abstract", "Richard Feynman was an American theoretical physicist.", "url",
        "https://en.wikipedia.org/wiki/Richard_Feynman", "source", "Wikipedia", "heading", "Richard Feynman");
    final CapturingConnectorService connector = new CapturingConnectorService(success(duckDuckGoResponse));
    final WebSearchTool tool = new WebSearchTool(connector);

    final Map<String, Object> result = tool.execute("richard feynman", null, null, null, false);

    assertThat(result).containsEntry("result", duckDuckGoResponse);
    assertThat(connector.lastConnectorId).isEqualTo("duckduckgo_instant_search");
    assertThat(connector.lastInput).containsEntry("query", "richard feynman");
  }

  @Test
  void fallsBackToBraveSearchWhenDuckDuckGoReturnsEmptyAbstractText() {
    final Map<String, Object> emptyResponse = Map.of("abstract", "", "url", "", "source", "", "heading", "");
    final CapturingConnectorService connector = new CapturingConnectorService(success(emptyResponse),
        success(Map.of("brave_result", true)));

    final WebSearchTool tool = new WebSearchTool(connector);

    final Map<String, Object> result = tool.execute("query", null, null, null, false);

    assertThat(result).containsEntry("result", Map.of("brave_result", true));
    assertThat(connector.callCount).isEqualTo(2);
    assertThat(connector.lastConnectorId).isEqualTo("brave_web_search");
  }

  @Test
  void fallsBackToBraveSearchWhenDuckDuckGoReturnsNullAbstractText() {
    final Map<String, Object> nullAbstractResponse = new java.util.LinkedHashMap<>();
    nullAbstractResponse.put("abstract", null);
    nullAbstractResponse.put("heading", "");
    final CapturingConnectorService connector = new CapturingConnectorService(success(nullAbstractResponse),
        success(Map.of("fallback", true)));

    final WebSearchTool tool = new WebSearchTool(connector);

    final Map<String, Object> result = tool.execute("query", null, null, null, false);

    assertThat(result).containsEntry("result", Map.of("fallback", true));
    assertThat(connector.callCount).isEqualTo(2);
  }

  @Test
  void returnsErrorWhenDuckDuckGoFails() {
    final WebSearchTool tool = new WebSearchTool(new StubConnectorService(failure("duckduckgo unavailable")));

    final Map<String, Object> result = tool.execute("query", null, null, null, false);

    assertThat(result).containsEntry("error", "duckduckgo unavailable");
  }

  @Test
  void usesBraveSearchWhenDetailedIsTrue() {
    final CapturingConnectorService connector = new CapturingConnectorService(success(Map.of("detailed", true)));
    final WebSearchTool tool = new WebSearchTool(connector);

    tool.execute("query", null, null, null, true);

    assertThat(connector.lastConnectorId).isEqualTo("brave_web_search");
  }

  private static ConnectorExecutionResult success(final Object data) {
    return new ConnectorExecutionResult(200, true, "https://example.com", "POST", Map.of(), data, null, null, null, null, false);
  }

  private static ConnectorExecutionResult failure(final String message) {
    return new ConnectorExecutionResult(503, false, "https://example.com", "POST", Map.of(), null, null, null, "SERVICE_UNAVAILABLE",
        message, true);
  }

  private record StubConnectorService(ConnectorExecutionResult response) implements ConnectorService {
    @Override
    public ConnectorExecutionResult execute(final String connectorId, final Map<String, Object> input) {
      return response;
    }
  }

  private static final class CapturingConnectorService implements ConnectorService {
    private final ConnectorExecutionResult[] responses;
    private int callIndex = 0;
    private String lastConnectorId;
    private Map<String, Object> lastInput;
    private int callCount = 0;

    private CapturingConnectorService(final ConnectorExecutionResult... responses) {
      this.responses = responses;
    }

    @Override
    public ConnectorExecutionResult execute(final String connectorId, final Map<String, Object> input) {
      this.lastConnectorId = connectorId;
      this.lastInput = input;
      this.callCount++;
      final int index = Math.min(callIndex, responses.length - 1);
      final ConnectorExecutionResult response = responses[index];
      callIndex++;
      return response;
    }
  }
}

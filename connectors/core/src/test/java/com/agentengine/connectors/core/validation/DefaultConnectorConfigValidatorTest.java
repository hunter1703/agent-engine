package com.agentengine.connectors.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.connectors.core.config.BodyConfig;
import com.agentengine.connectors.core.config.BodyType;
import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.config.EndpointConfig;
import com.agentengine.connectors.core.config.HttpMethod;
import com.agentengine.connectors.core.config.PaginationConfig;
import com.agentengine.connectors.core.config.PaginationType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class DefaultConnectorConfigValidatorTest {

  private final DefaultConnectorConfigValidator validator = new DefaultConnectorConfigValidator();

  @Test
  public void validDefinitionPasses() {
    final ConfigValidationResult result = validator.validate(validDefinition());
    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  public void emptyIdFailsValidation() {
    final ConnectorDefinition definition = withId(validDefinition(), " ");
    final ConfigValidationResult result = validator.validate(definition);
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(issue -> issue.path().equals("id"));
  }

  @Test
  public void missingEndpointFailsValidation() {
    final ConnectorDefinition definition =
        new ConnectorDefinition(
            "test-id", null, null, null, null, null, null, null, null, null, null, true);

    final ConfigValidationResult result = validator.validate(definition);
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(issue -> issue.path().equals("endpoint"));
  }

  @Test
  public void bodyWithGetMethodFailsValidation() {
    final ConnectorDefinition definition =
        new ConnectorDefinition(
            "test-id",
            null,
            endpoint(HttpMethod.GET, "https://api.example.com"),
            null,
            null,
            new BodyConfig(BodyType.JSON, Map.of("foo", "bar"), null, true),
            null,
            null,
            null,
            null,
            null,
            true);

    final ConfigValidationResult result = validator.validate(definition);
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(issue -> issue.path().equals("body"));
  }

  @Test
  public void invalidPaginationConfigFailsValidation() {
    final ConnectorDefinition definition =
        new ConnectorDefinition(
            "test-id",
            null,
            endpoint(HttpMethod.GET, "https://api.example.com"),
            null,
            null,
            null,
            null,
            null,
            new PaginationConfig(
                PaginationType.CURSOR,
                10,
                null,
                1,
                null,
                100,
                null,
                0,
                null,
                100,
                null,
                null,
                null),
            null,
            null,
            true);

    final ConfigValidationResult result = validator.validate(definition);
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors())
        .anyMatch(issue -> issue.path().equals("pagination.cursorParam"))
        .anyMatch(issue -> issue.path().equals("pagination.nextCursor"));
  }

  @Test
  public void validateOrThrowThrowsForInvalidDefinition() {
    final ConnectorDefinition invalidDefinition =
        new ConnectorDefinition(
            "",
            null,
            endpoint(HttpMethod.UNKNOWN, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true);

    assertThatThrownBy(() -> validator.validateOrThrow(invalidDefinition))
        .isInstanceOf(ConnectorConfigValidationException.class)
        .hasMessageContaining("validation failed");
  }

  private static ConnectorDefinition validDefinition() {
    return new ConnectorDefinition(
        "test-id",
        null,
        endpoint(HttpMethod.GET, "https://api.example.com"),
        Map.of("Accept", "application/json"),
        Map.of("limit", 10),
        null,
        null,
        null,
        PaginationConfig.none(),
        null,
        List.of(),
        true);
  }

  private static ConnectorDefinition withId(final ConnectorDefinition definition, final String id) {
    return new ConnectorDefinition(
        id,
        definition.appName(),
        definition.endpoint(),
        definition.headers(),
        definition.query(),
        definition.body(),
        definition.auth(),
        definition.retryPolicy(),
        definition.pagination(),
        definition.responseMapping(),
        definition.errorMappings(),
        definition.strictUnresolvedVariables());
  }

  private static EndpointConfig endpoint(final HttpMethod method, final String baseUrl) {
    return new EndpointConfig(
        method, baseUrl, null, "/v1/test", null, 1_000L, 1_000L, 1_000L, true, true);
  }
}

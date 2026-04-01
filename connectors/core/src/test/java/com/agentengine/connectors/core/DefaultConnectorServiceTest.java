package com.agentengine.connectors.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.config.EndpointConfig;
import com.agentengine.connectors.core.config.HttpMethod;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import com.agentengine.connectors.core.runtime.ConnectorExecutor;
import com.agentengine.connectors.core.runtime.PaginatedExecutionResult;
import com.agentengine.connectors.core.runtime.RequestContext;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultConnectorServiceTest {

    @Test
    void injectsAuthMaterialsIntoContextAuthAndInputAuth() {
        final CapturingExecutor executor = new CapturingExecutor();
        final DefaultConnectorService service = new DefaultConnectorService(
                connectorId -> Optional.of(connectorDefinition()),
                executor,
                connectorId -> Map.of("token", "secret-token"));

        service.execute("test", Map.of("query", "hello"));

        assertThat(executor.capturedContext.auth()).containsEntry("token", "secret-token");
        assertThat(executor.capturedContext.connection().appName()).isEqualTo("test-app");
        assertThat(executor.capturedContext.connection().inputs()).containsEntry("token", "secret-token");
        assertThat(executor.capturedContext.input())
                .containsEntry("query", "hello")
                .containsEntry("auth", Map.of("token", "secret-token"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void preservesExistingInputAuthAndOverlaysRepositoryAuth() {
        final CapturingExecutor executor = new CapturingExecutor();
        final DefaultConnectorService service = new DefaultConnectorService(
                connectorId -> Optional.of(connectorDefinition()),
                executor,
                connectorId -> Map.of("token", "repo-token", "tenant", "alpha"));

        service.execute("test", Map.of("auth", Map.of("token", "input-token", "region", "us")));

        assertThat(executor.capturedContext.auth())
                .containsEntry("token", "repo-token")
                .containsEntry("tenant", "alpha");

        final Map<String, Object> inputAuth =
                (Map<String, Object>) executor.capturedContext.input().get("auth");
        assertThat(inputAuth)
                .containsEntry("token", "repo-token")
                .containsEntry("tenant", "alpha")
                .containsEntry("region", "us");
    }

    private static ConnectorDefinition connectorDefinition() {
        return new ConnectorDefinition(
                "test",
                "test-app",
                new EndpointConfig(
                        HttpMethod.GET, "https://example.com", null, "/", null, null, null, null, true, true),
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                true);
    }

    private static final class CapturingExecutor implements ConnectorExecutor {
        private RequestContext capturedContext;

        @Override
        public ConnectorExecutionResult executeOnce(
                final ConnectorDefinition definition, final RequestContext context) {
            this.capturedContext = context;
            return new ConnectorExecutionResult(
                    200, true, "https://example.com", "GET", Map.of(), Map.of(), null, null, null, null, false);
        }

        @Override
        public PaginatedExecutionResult executeAllPages(
                final ConnectorDefinition definition, final RequestContext context) {
            throw new UnsupportedOperationException("Not needed");
        }
    }
}

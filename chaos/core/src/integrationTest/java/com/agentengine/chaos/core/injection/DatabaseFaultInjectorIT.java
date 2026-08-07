package com.agentengine.chaos.core.injection;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.DatabaseFailureParameters;
import com.agentengine.chaos.api.fault.DatabaseTarget;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies {@link DatabaseFaultInjector} against a real Toxiproxy instance sitting in front of a
 * real PostgreSQL container: injecting {@link FaultType#EVENT_JOURNAL_FAILURE} must block new TCP
 * connections through the proxy, and removing the fault must restore connectivity within the
 * 30-second recovery window.
 */
@Testcontainers
class DatabaseFaultInjectorIT {

    private static final Network NETWORK = Network.newNetwork();

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgres:16-alpine"))
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres");

    private static final ToxiproxyContainer TOXIPROXY =
            new ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.9.0")).withNetwork(NETWORK);

    private static ToxiproxyContainer.ContainerProxy postgresProxy;
    private static DatabaseFaultInjector injector;

    @BeforeAll
    static void startContainers() {
        POSTGRES.start();
        TOXIPROXY.start();

        postgresProxy = TOXIPROXY.getProxy(POSTGRES, PostgreSQLContainer.POSTGRESQL_PORT);
        final ToxiproxyClient toxiproxyClient = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());

        injector = new DatabaseFaultInjector(resolveManagedProxies(toxiproxyClient));
    }

    private static Map<String, Proxy> resolveManagedProxies(final ToxiproxyClient toxiproxyClient) {
        try {
            final Proxy postgres = toxiproxyClient.getProxy(postgresProxy.getName());
            return Map.of("postgresql", postgres, "mongodb", postgres, "qdrant", postgres);
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to resolve Toxiproxy proxies for test setup", ex);
        }
    }

    @AfterAll
    static void stopContainers() {
        TOXIPROXY.stop();
        POSTGRES.stop();
        NETWORK.close();
    }

    @Test
    void shouldBlockAndThenRestoreConnectionsThroughTheProxy() throws ExecutionException, InterruptedException {
        assertThat(canConnect()).isTrue();

        final String faultId = injector.injectFault(
                        FaultType.EVENT_JOURNAL_FAILURE,
                        new TargetSelector("agent-engine", "runtime", Map.of(), Optional.empty()),
                        new DatabaseFailureParameters(DatabaseTarget.POSTGRESQL),
                        new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0))
                .toCompletableFuture()
                .get();

        assertThat(canConnect()).isFalse();

        injector.removeFault(faultId).toCompletableFuture().get();

        final boolean reconnected = DatabaseReconnectionValidator.verifyReconnection(
                DatabaseFaultInjectorIT::canConnect, Duration.ofSeconds(30));
        assertThat(reconnected).isTrue();
    }

    /**
     * Connects through the Toxiproxy-proxied port, sends a byte, and waits for any response.
     * A plain TCP {@code connect()} alone is not enough to detect a {@code timeout} toxic —
     * Toxiproxy still completes the handshake and simply stops forwarding bytes afterward — so
     * this checks that data actually flows end to end.
     */
    private static boolean canConnect() {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(postgresProxy.getContainerIpAddress(), postgresProxy.getProxyPort()), 1_000);
            socket.setSoTimeout(1_000);
            socket.getOutputStream().write(new byte[] {0, 0, 0, 0});
            socket.getOutputStream().flush();
            return socket.getInputStream().read() != -1;
        } catch (final IOException ex) {
            return false;
        }
    }
}

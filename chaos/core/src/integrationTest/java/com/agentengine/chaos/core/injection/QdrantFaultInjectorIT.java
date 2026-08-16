package com.agentengine.chaos.core.injection;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.QdrantFailureParameters;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the {@link FaultType#QDRANT_FAILURE} toxics against a real Toxiproxy instance. There is
 * no Qdrant Testcontainers module, so an {@code nginx:alpine} container stands in for the Qdrant
 * HTTP API — this test exercises Toxiproxy toxic application/removal semantics, not real Qdrant
 * behavior.
 */
@Testcontainers
class QdrantFaultInjectorIT {

  private static final Network NETWORK = Network.newNetwork();

  private static final GenericContainer<?> QDRANT_STAND_IN =
      new GenericContainer<>(DockerImageName.parse("nginx:alpine"))
          .withNetwork(NETWORK)
          .withNetworkAliases("qdrant-stand-in")
          .withExposedPorts(80)
          .waitingFor(Wait.forHttp("/").forStatusCode(200));

  private static final ToxiproxyContainer TOXIPROXY =
      new ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.9.0"))
          .withNetwork(NETWORK);

  private static ToxiproxyContainer.ContainerProxy qdrantProxy;
  private static DatabaseFaultInjector injector;
  private static HttpClient httpClient;

  @BeforeAll
  static void startContainers() throws IOException {
    QDRANT_STAND_IN.start();
    TOXIPROXY.start();

    qdrantProxy = TOXIPROXY.getProxy(QDRANT_STAND_IN, 80);
    final ToxiproxyClient toxiproxyClient =
        new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
    final Proxy qdrant = toxiproxyClient.getProxy(qdrantProxy.getName());

    injector =
        new DatabaseFaultInjector(
            Map.of("postgresql", qdrant, "mongodb", qdrant, "qdrant", qdrant));
    httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  @AfterAll
  static void stopContainers() {
    TOXIPROXY.stop();
    QDRANT_STAND_IN.stop();
    NETWORK.close();
  }

  @Test
  void shouldFullyBlockAndThenRestoreRequestsWhenLatencyAbsent()
      throws ExecutionException, InterruptedException {
    assertThat(requestSucceedsWithin(Duration.ofSeconds(2))).isTrue();

    final String faultId =
        injector
            .injectFault(
                FaultType.QDRANT_FAILURE,
                new TargetSelector("agent-engine", "runtime", Map.of(), Optional.empty()),
                new QdrantFailureParameters(Optional.empty()),
                new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0))
            .toCompletableFuture()
            .get();

    assertThat(requestSucceedsWithin(Duration.ofSeconds(2))).isFalse();

    injector.removeFault(faultId).toCompletableFuture().get();

    final boolean reconnected =
        DatabaseReconnectionValidator.verifyReconnection(
            () -> requestSucceedsWithin(Duration.ofSeconds(2)), Duration.ofSeconds(30));
    assertThat(reconnected).isTrue();
  }

  @Test
  void shouldApplyAndRemoveLatencyToxicWithoutBlockingRequests()
      throws ExecutionException, InterruptedException {
    final String faultId =
        injector
            .injectFault(
                FaultType.QDRANT_FAILURE,
                new TargetSelector("agent-engine", "runtime", Map.of(), Optional.empty()),
                new QdrantFailureParameters(Optional.of(Duration.ofMillis(500))),
                new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0))
            .toCompletableFuture()
            .get();

    // A generous timeout confirms the request still completes — latency degrades, it doesn't block.
    assertThat(requestSucceedsWithin(Duration.ofSeconds(5))).isTrue();

    injector.removeFault(faultId).toCompletableFuture().get();

    assertThat(requestSucceedsWithin(Duration.ofSeconds(2))).isTrue();
  }

  private static boolean requestSucceedsWithin(final Duration timeout) {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "http://"
                        + qdrantProxy.getContainerIpAddress()
                        + ":"
                        + qdrantProxy.getProxyPort()
                        + "/"))
            .timeout(timeout)
            .GET()
            .build();
    try {
      final HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (final IOException ex) {
      return false;
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}

package com.agentengine.chaos.core.injection;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.LlmProviderLatencyParameters;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmProviderFaultInjectorTest {

  private static final BlastRadius BLAST_RADIUS =
      new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0);

  private WireMockServer server;
  private LlmProviderFaultInjector injector;
  private OkHttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void startWireMock() {
    server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    injector = new LlmProviderFaultInjector(new WireMock("localhost", server.port()));
    httpClient = new OkHttpClient();
    baseUrl = server.baseUrl();
  }

  @AfterEach
  void stopWireMock() {
    server.stop();
  }

  @Test
  void shouldSupportOnlyLlmAndConnectorFaultTypes() {
    assertThat(injector.supports(FaultType.LLM_PROVIDER_UNAVAILABLE)).isTrue();
    assertThat(injector.supports(FaultType.LLM_PROVIDER_LATENCY)).isTrue();
    assertThat(injector.supports(FaultType.CONNECTOR_FAILURE)).isTrue();
    assertThat(injector.supports(FaultType.POD_KILL)).isFalse();
  }

  @Test
  void shouldReturn503WhenLlmProviderUnavailableInjected()
      throws ExecutionException, InterruptedException, IOException {
    final TargetSelector target =
        new TargetSelector("agent-engine", "/v1/chat/completions", Map.of(), Optional.empty());

    injector
        .injectFault(FaultType.LLM_PROVIDER_UNAVAILABLE, target, null, BLAST_RADIUS)
        .toCompletableFuture()
        .get();

    try (Response response = get("/v1/chat/completions")) {
      assertThat(response.code()).isEqualTo(503);
    }
  }

  @Test
  void shouldRemoveStubSoRequestNoLongerMatchesAfterRemoveFault()
      throws ExecutionException, InterruptedException, IOException {
    final TargetSelector target =
        new TargetSelector("agent-engine", "/v1/chat/completions", Map.of(), Optional.empty());

    final String faultId =
        injector
            .injectFault(FaultType.LLM_PROVIDER_UNAVAILABLE, target, null, BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    injector.removeFault(faultId).toCompletableFuture().get();

    // With no stub registered, WireMock's default-not-configured response is 404.
    try (Response response = get("/v1/chat/completions")) {
      assertThat(response.code()).isEqualTo(404);
    }
  }

  @Test
  void shouldApplyFixedDelayWhenLlmProviderLatencyInjected()
      throws ExecutionException, InterruptedException, IOException {
    final TargetSelector target =
        new TargetSelector("agent-engine", "/v1/chat/completions", Map.of(), Optional.empty());
    final Duration latency = Duration.ofMillis(750);

    injector
        .injectFault(
            FaultType.LLM_PROVIDER_LATENCY,
            target,
            new LlmProviderLatencyParameters(latency),
            BLAST_RADIUS)
        .toCompletableFuture()
        .get();

    final long start = System.nanoTime();
    try (Response response = get("/v1/chat/completions")) {
      final long elapsed = (System.nanoTime() - start) / 1_000_000;
      assertThat(response.code()).isEqualTo(200);
      assertThat(elapsed).isGreaterThanOrEqualTo(latency.toMillis());
    }
  }

  @Test
  void shouldResetConnectionWhenConnectorFailureInjected()
      throws ExecutionException, InterruptedException {
    final TargetSelector target =
        new TargetSelector("agent-engine", "/connectors/.*", Map.of(), Optional.empty());

    injector
        .injectFault(FaultType.CONNECTOR_FAILURE, target, null, BLAST_RADIUS)
        .toCompletableFuture()
        .get();

    assertThatThrownBy(() -> get("/connectors/example").close()).isInstanceOf(IOException.class);
  }

  private Response get(final String path) throws IOException {
    return httpClient.newCall(new Request.Builder().url(baseUrl + path).build()).execute();
  }
}

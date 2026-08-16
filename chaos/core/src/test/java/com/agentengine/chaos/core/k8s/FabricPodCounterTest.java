package com.agentengine.chaos.core.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FabricPodCounterTest {

  private static final String NAMESPACE = "agent-engine";

  private KubernetesMockServer mockServer;
  private KubernetesClient client;
  private FabricPodCounter podCounter;

  @BeforeEach
  void setUp() {
    final Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
    mockServer =
        new KubernetesMockServer(
            new Context(Serialization.jsonMapper()),
            new MockWebServer(),
            responses,
            new KubernetesMixedDispatcher(responses),
            true);
    mockServer.init();
    client = mockServer.createClient();
    podCounter = new FabricPodCounter(client);
  }

  @AfterEach
  void tearDown() {
    mockServer.destroy();
  }

  @Test
  void shouldCountPodsMatchingLabels() {
    createPod("runtime-1", Map.of("app", "runtime"));
    createPod("runtime-2", Map.of("app", "runtime"));
    createPod("gateway-1", Map.of("app", "gateway"));

    final int matching = podCounter.countMatchingPods(NAMESPACE, Map.of("app", "runtime"));

    assertThat(matching).isEqualTo(2);
  }

  @Test
  void shouldReturnZeroWhenNoPodsMatchLabels() {
    createPod("gateway-1", Map.of("app", "gateway"));

    final int matching = podCounter.countMatchingPods(NAMESPACE, Map.of("app", "runtime"));

    assertThat(matching).isZero();
  }

  @Test
  void shouldCountServicePodsUsingServiceSelector() {
    createPod("runtime-1", Map.of("app", "runtime", "tier", "backend"));
    createPod("runtime-2", Map.of("app", "runtime", "tier", "backend"));
    createPod("runtime-canary", Map.of("app", "runtime", "tier", "canary"));
    client
        .services()
        .inNamespace(NAMESPACE)
        .resource(
            new ServiceBuilder()
                .withNewMetadata()
                .withName("runtime")
                .withNamespace(NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withSelector(Map.of("app", "runtime", "tier", "backend"))
                .endSpec()
                .build())
        .create();

    final int servicePods = podCounter.countServicePods(NAMESPACE, "runtime");

    assertThat(servicePods).isEqualTo(2);
  }

  @Test
  void shouldFallBackToAppLabelWhenServiceHasNoSelector() {
    createPod("worker-1", Map.of("app", "worker"));
    createPod("worker-2", Map.of("app", "worker"));

    final int servicePods = podCounter.countServicePods(NAMESPACE, "worker");

    assertThat(servicePods).isEqualTo(2);
  }

  private void createPod(final String name, final Map<String, String> labels) {
    client
        .pods()
        .inNamespace(NAMESPACE)
        .resource(
            new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(NAMESPACE)
                .withLabels(labels)
                .endMetadata()
                .build())
        .create();
  }
}

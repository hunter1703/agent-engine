package com.agentengine.chaos.core.injection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.CpuStressParameters;
import com.agentengine.chaos.api.fault.NetworkLatencyParameters;
import com.agentengine.chaos.api.fault.PodKillParameters;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaosMeshFaultInjectorTest {

    private static final String NAMESPACE = "agent-engine";
    private static final BlastRadius BLAST_RADIUS = new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0);

    private KubernetesMockServer mockServer;
    private KubernetesClient client;
    private ChaosMeshFaultInjector injector;

    @BeforeEach
    void setUp() {
        final Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
        mockServer = new KubernetesMockServer(
                new Context(Serialization.jsonMapper()),
                new MockWebServer(),
                responses,
                new KubernetesMixedDispatcher(responses),
                true);
        mockServer.init();
        client = mockServer.createClient();
        injector = new ChaosMeshFaultInjector(client);
    }

    @AfterEach
    void tearDown() {
        mockServer.destroy();
    }

    @Test
    void shouldSupportOnlyChaosMeshFaultTypes() {
        assertThat(injector.supports(FaultType.POD_KILL)).isTrue();
        assertThat(injector.supports(FaultType.NETWORK_PARTITION)).isTrue();
        assertThat(injector.supports(FaultType.NETWORK_LATENCY)).isTrue();
        assertThat(injector.supports(FaultType.NETWORK_PACKET_LOSS)).isTrue();
        assertThat(injector.supports(FaultType.CLUSTER_PARTITION)).isTrue();
        assertThat(injector.supports(FaultType.CPU_STRESS)).isTrue();
        assertThat(injector.supports(FaultType.MEMORY_STRESS)).isTrue();
        assertThat(injector.supports(FaultType.DISK_STRESS)).isTrue();
        assertThat(injector.supports(FaultType.MESSAGE_DROP)).isFalse();
        assertThat(injector.supports(FaultType.DATABASE_FAILURE)).isFalse();
    }

    @Test
    void shouldCreatePodChaosResourceForPodKill() throws ExecutionException, InterruptedException {
        final TargetSelector target =
                new TargetSelector(NAMESPACE, "runtime", Map.of("app", "runtime"), Optional.empty());

        final String faultId = injector.injectFault(FaultType.POD_KILL, target, new PodKillParameters(2), BLAST_RADIUS)
                .toCompletableFuture()
                .get();

        assertThat(faultId).startsWith("PodChaos/" + NAMESPACE + "/experiment-");

        final GenericKubernetesResource created = fetch("PodChaos", "podchaos", faultId);
        assertThat(created.getApiVersion()).isEqualTo("chaos-mesh.org/v1alpha1");
        assertThat(created.getKind()).isEqualTo("PodChaos");
        assertThat(created.getMetadata().getNamespace()).isEqualTo(NAMESPACE);

        @SuppressWarnings("unchecked")
        final Map<String, Object> spec =
                (Map<String, Object>) created.getAdditionalProperties().get("spec");
        assertThat(spec.get("action")).isEqualTo("pod-kill");
        assertThat(spec.get("mode")).isEqualTo("fixed");
        assertThat(spec.get("value")).isEqualTo("2");
        assertThat(spec.get("selector")).isEqualTo(Map.of("labelSelectors", Map.of("app", "runtime")));
    }

    @Test
    void shouldCreateNetworkChaosResourceForNetworkLatency() throws ExecutionException, InterruptedException {
        final TargetSelector target =
                new TargetSelector(NAMESPACE, "runtime", Map.of("app", "runtime"), Optional.empty());

        final String faultId = injector.injectFault(
                        FaultType.NETWORK_LATENCY,
                        target,
                        new NetworkLatencyParameters(Duration.ofMillis(500)),
                        BLAST_RADIUS)
                .toCompletableFuture()
                .get();

        assertThat(faultId).startsWith("NetworkChaos/" + NAMESPACE + "/experiment-");

        final GenericKubernetesResource created = fetch("NetworkChaos", "networkchaos", faultId);
        @SuppressWarnings("unchecked")
        final Map<String, Object> spec =
                (Map<String, Object>) created.getAdditionalProperties().get("spec");
        assertThat(spec.get("action")).isEqualTo("delay");
        assertThat(spec.get("delay")).isEqualTo(Map.of("latency", "500ms"));
    }

    @Test
    void shouldCreateStressChaosResourceForCpuStress() throws ExecutionException, InterruptedException {
        final TargetSelector target =
                new TargetSelector(NAMESPACE, "runtime", Map.of("app", "runtime"), Optional.empty());

        final String faultId = injector.injectFault(
                        FaultType.CPU_STRESS, target, new CpuStressParameters(80), BLAST_RADIUS)
                .toCompletableFuture()
                .get();

        assertThat(faultId).startsWith("StressChaos/" + NAMESPACE + "/experiment-");

        final GenericKubernetesResource created = fetch("StressChaos", "stresschaos", faultId);
        @SuppressWarnings("unchecked")
        final Map<String, Object> spec =
                (Map<String, Object>) created.getAdditionalProperties().get("spec");
        @SuppressWarnings("unchecked")
        final Map<String, Object> stressors = (Map<String, Object>) spec.get("stressors");
        @SuppressWarnings("unchecked")
        final Map<String, Object> cpu = (Map<String, Object>) stressors.get("cpu");
        assertThat(cpu.get("load")).isEqualTo(80);
    }

    @Test
    void shouldDeleteResourceOnRemoveFault() throws ExecutionException, InterruptedException {
        final TargetSelector target =
                new TargetSelector(NAMESPACE, "runtime", Map.of("app", "runtime"), Optional.empty());
        final String faultId = injector.injectFault(FaultType.POD_KILL, target, new PodKillParameters(1), BLAST_RADIUS)
                .toCompletableFuture()
                .get();

        injector.removeFault(faultId).toCompletableFuture().get();

        final String name = faultId.substring(faultId.lastIndexOf('/') + 1);
        final GenericKubernetesResource fetched = client.genericKubernetesResources(context("podchaos"))
                .inNamespace(NAMESPACE)
                .withName(name)
                .get();
        assertThat(fetched).isNull();
    }

    @Test
    void shouldTreatRemovingAlreadyDeletedFaultAsSuccess() throws ExecutionException, InterruptedException {
        final String faultId = "PodChaos/" + NAMESPACE + "/experiment-does-not-exist";

        injector.removeFault(faultId).toCompletableFuture().get();
    }

    @Test
    void shouldRejectUnsupportedFaultType() {
        final TargetSelector target =
                new TargetSelector(NAMESPACE, "runtime", Map.of("app", "runtime"), Optional.empty());

        assertThatThrownBy(() -> injector.injectFault(
                                FaultType.MESSAGE_DROP, target, new PodKillParameters(1), BLAST_RADIUS)
                        .toCompletableFuture()
                        .get())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    private GenericKubernetesResource fetch(final String kind, final String plural, final String faultId) {
        final String name = faultId.substring(faultId.lastIndexOf('/') + 1);
        return client.genericKubernetesResources(context(plural))
                .inNamespace(NAMESPACE)
                .withName(name)
                .get();
    }

    private static ResourceDefinitionContext context(final String plural) {
        return new ResourceDefinitionContext.Builder()
                .withGroup("chaos-mesh.org")
                .withVersion("v1alpha1")
                .withPlural(plural)
                .withNamespaced(true)
                .build();
    }
}

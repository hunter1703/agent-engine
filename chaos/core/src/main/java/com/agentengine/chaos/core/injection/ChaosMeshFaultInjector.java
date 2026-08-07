package com.agentengine.chaos.core.injection;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.CpuStressParameters;
import com.agentengine.chaos.api.fault.DiskStressParameters;
import com.agentengine.chaos.api.fault.FaultParameters;
import com.agentengine.chaos.api.fault.MemoryStressParameters;
import com.agentengine.chaos.api.fault.NetworkLatencyParameters;
import com.agentengine.chaos.api.fault.NetworkPartitionParameters;
import com.agentengine.chaos.api.fault.PacketLossParameters;
import com.agentengine.chaos.api.fault.PodKillParameters;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Injects infrastructure-level faults ({@link FaultType#POD_KILL}, network, stress, and I/O
 * faults) by creating Chaos Mesh custom resources via the Fabric8 {@link GenericKubernetesResource}
 * API. Chaos Mesh's own controller drives the fault lifecycle from there — killing pods,
 * throttling network traffic, or injecting stress — so this class only needs to create and delete
 * the CR describing the experiment; it never talks to the target pods directly.
 *
 * <p>The fault ID returned by {@link #injectFault} encodes the CRD kind, namespace, and generated
 * resource name (e.g. {@code "PodChaos/agent-engine/experiment-<uuid>"}) so {@link #removeFault}
 * can delete the exact resource without a separate lookup. A 404 on delete means Chaos Mesh (or
 * an operator) already removed the resource, which is the same end state {@code removeFault} is
 * trying to reach, so it is treated as success rather than an error.
 */
public final class ChaosMeshFaultInjector implements FaultInjector {

    private static final String API_VERSION = "chaos-mesh.org/v1alpha1";
    private static final String ARTERY_PORT_LABEL = "port";
    private static final int ARTERY_PORT = 2552;
    private static final int NOT_FOUND = 404;

    private final KubernetesClient client;

    public ChaosMeshFaultInjector(final KubernetesClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<String> injectFault(
            final FaultType faultType,
            final TargetSelector target,
            final FaultParameters parameters,
            final BlastRadius blastRadius) {
        return CompletableFuture.supplyAsync(() -> {
            final String kind = kindFor(faultType);
            final String name = "experiment-" + UUID.randomUUID();
            final GenericKubernetesResource resource = new GenericKubernetesResourceBuilder()
                    .withApiVersion(API_VERSION)
                    .withKind(kind)
                    .withNewMetadata()
                    .withName(name)
                    .withNamespace(target.namespace())
                    .endMetadata()
                    .addToAdditionalProperties("spec", specFor(faultType, target, parameters))
                    .build();
            client.genericKubernetesResources(resourceDefinitionFor(kind))
                    .inNamespace(target.namespace())
                    .resource(resource)
                    .create();
            return kind + "/" + target.namespace() + "/" + name;
        });
    }

    @Override
    public CompletionStage<Void> removeFault(final String faultId) {
        return CompletableFuture.runAsync(() -> {
            final String[] parts = faultId.split("/", 3);
            final String kind = parts[0];
            final String namespace = parts[1];
            final String name = parts[2];
            try {
                client.genericKubernetesResources(resourceDefinitionFor(kind))
                        .inNamespace(namespace)
                        .withName(name)
                        .delete();
            } catch (final KubernetesClientException ex) {
                if (ex.getCode() != NOT_FOUND) {
                    throw ex;
                }
            }
        });
    }

    @Override
    public boolean supports(final FaultType faultType) {
        return switch (faultType) {
            case POD_KILL,
                    NETWORK_PARTITION,
                    NETWORK_LATENCY,
                    NETWORK_PACKET_LOSS,
                    CLUSTER_PARTITION,
                    CPU_STRESS,
                    MEMORY_STRESS,
                    DISK_STRESS -> true;
            default -> false;
        };
    }

    private static String kindFor(final FaultType faultType) {
        return switch (faultType) {
            case POD_KILL -> "PodChaos";
            case NETWORK_PARTITION, NETWORK_LATENCY, NETWORK_PACKET_LOSS, CLUSTER_PARTITION -> "NetworkChaos";
            case CPU_STRESS, MEMORY_STRESS -> "StressChaos";
            case DISK_STRESS -> "IOChaos";
            default -> throw new IllegalArgumentException("ChaosMeshFaultInjector does not support " + faultType);
        };
    }

    /** Chaos Mesh CRD plurals are simply the kind lowercased, e.g. {@code PodChaos} → {@code podchaos}. */
    private static ResourceDefinitionContext resourceDefinitionFor(final String kind) {
        return new ResourceDefinitionContext.Builder()
                .withGroup("chaos-mesh.org")
                .withVersion("v1alpha1")
                .withPlural(kind.toLowerCase(Locale.ROOT))
                .withNamespaced(true)
                .build();
    }

    private static Map<String, Object> specFor(
            final FaultType faultType, final TargetSelector target, final FaultParameters parameters) {
        return switch (faultType) {
            case POD_KILL -> podKillSpec(target, (PodKillParameters) parameters);
            case NETWORK_PARTITION -> networkPartitionSpec(target, (NetworkPartitionParameters) parameters);
            case NETWORK_LATENCY -> networkLatencySpec(target, (NetworkLatencyParameters) parameters);
            case NETWORK_PACKET_LOSS -> networkPacketLossSpec(target, (PacketLossParameters) parameters);
            case CLUSTER_PARTITION -> clusterPartitionSpec(target);
            case CPU_STRESS -> cpuStressSpec(target, (CpuStressParameters) parameters);
            case MEMORY_STRESS -> memoryStressSpec(target, (MemoryStressParameters) parameters);
            case DISK_STRESS -> diskStressSpec(target, (DiskStressParameters) parameters);
            default -> throw new IllegalArgumentException("ChaosMeshFaultInjector does not support " + faultType);
        };
    }

    private static Map<String, Object> podKillSpec(final TargetSelector target, final PodKillParameters parameters) {
        return Map.of(
                "action",
                "pod-kill",
                "mode",
                "fixed",
                "value",
                String.valueOf(parameters.count()),
                "selector",
                Map.of("labelSelectors", target.podLabels()));
    }

    private static Map<String, Object> networkPartitionSpec(
            final TargetSelector target, final NetworkPartitionParameters parameters) {
        return Map.of(
                "action", "partition",
                "mode", "all",
                "direction", "both",
                "selector", Map.of("labelSelectors", target.podLabels()),
                "target", Map.of("selector", Map.of("services", parameters.blockedServices()), "mode", "all"));
    }

    private static Map<String, Object> networkLatencySpec(
            final TargetSelector target, final NetworkLatencyParameters parameters) {
        return Map.of(
                "action",
                "delay",
                "mode",
                "all",
                "selector",
                Map.of("labelSelectors", target.podLabels()),
                "delay",
                Map.of("latency", formatDuration(parameters.latency())));
    }

    private static Map<String, Object> networkPacketLossSpec(
            final TargetSelector target, final PacketLossParameters parameters) {
        return Map.of(
                "action",
                "loss",
                "mode",
                "all",
                "selector",
                Map.of("labelSelectors", target.podLabels()),
                "loss",
                Map.of("loss", String.valueOf(parameters.lossPercentage())));
    }

    private static Map<String, Object> clusterPartitionSpec(final TargetSelector target) {
        return Map.of(
                "action",
                "partition",
                "mode",
                "all",
                "direction",
                "both",
                "selector",
                Map.of("labelSelectors", target.podLabels()),
                "target",
                Map.of("selector", Map.of("labelSelectors", target.podLabels()), "mode", "all"),
                "podRule",
                Map.of("ports", List.of(Map.of(ARTERY_PORT_LABEL, ARTERY_PORT))));
    }

    private static Map<String, Object> cpuStressSpec(
            final TargetSelector target, final CpuStressParameters parameters) {
        return Map.of(
                "mode", "all",
                "selector", Map.of("labelSelectors", target.podLabels()),
                "stressors", Map.of("cpu", Map.of("workers", 1, "load", parameters.cpuPercentage())));
    }

    private static Map<String, Object> memoryStressSpec(
            final TargetSelector target, final MemoryStressParameters parameters) {
        return Map.of(
                "mode", "all",
                "selector", Map.of("labelSelectors", target.podLabels()),
                "stressors", Map.of("memory", Map.of("workers", 1, "size", parameters.memoryLimit())));
    }

    private static Map<String, Object> diskStressSpec(
            final TargetSelector target, final DiskStressParameters parameters) {
        return Map.of(
                "action", "mixed",
                "mode", "all",
                "selector", Map.of("labelSelectors", target.podLabels()),
                "addr", "0.0.0.0:65534",
                "mixed", Map.of("size", parameters.diskIORate()));
    }

    private static String formatDuration(final Duration duration) {
        return duration.toMillis() + "ms";
    }
}

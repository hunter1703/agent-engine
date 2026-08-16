package com.agentengine.chaos.core.k8s;

import java.util.Map;

/**
 * Counts pods for blast-radius enforcement. Implemented against the real Kubernetes API by {@code
 * ChaosMeshFaultInjector}'s supporting infrastructure; kept as a narrow interface here so {@code
 * BlastRadiusEnforcer} doesn't need a Kubernetes client dependency.
 */
public interface PodCounter {

  /** Pods matching the experiment's label selector — the pods that would actually be faulted. */
  int countMatchingPods(String namespace, Map<String, String> podLabels);

  /** Total pods backing a service — the denominator for {@code BlastRadius.maxPercentage}. */
  int countServicePods(String namespace, String service);
}

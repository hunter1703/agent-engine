package com.agentengine.chaos.core.k8s;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Map;

/**
 * Counts pods against the live Kubernetes API using Fabric8, backing {@code BlastRadiusEnforcer}'s
 * pre-flight checks. {@link #countServicePods} prefers the named {@link Service}'s selector since
 * that is the authoritative set of pods it routes to; when no such service exists (or it declares
 * no selector), it falls back to the {@code app=<service>} label convention used elsewhere in this
 * codebase.
 */
public final class FabricPodCounter implements PodCounter {

  private final KubernetesClient client;

  public FabricPodCounter(final KubernetesClient client) {
    this.client = client;
  }

  @Override
  public int countMatchingPods(final String namespace, final Map<String, String> podLabels) {
    return client.pods().inNamespace(namespace).withLabels(podLabels).list().getItems().size();
  }

  @Override
  public int countServicePods(final String namespace, final String service) {
    final Map<String, String> selector = serviceSelector(namespace, service);
    return countMatchingPods(namespace, selector);
  }

  private Map<String, String> serviceSelector(final String namespace, final String service) {
    final Service resource = client.services().inNamespace(namespace).withName(service).get();
    if (resource != null
        && resource.getSpec() != null
        && resource.getSpec().getSelector() != null) {
      return resource.getSpec().getSelector();
    }
    return Map.of("app", service);
  }
}

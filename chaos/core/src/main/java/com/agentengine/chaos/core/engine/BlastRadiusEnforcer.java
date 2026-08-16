package com.agentengine.chaos.core.engine;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.core.k8s.PodCounter;

/**
 * Pre-flight safety check run before any fault is injected. Only meaningful for pod-targeted faults
 * (Chaos Mesh infrastructure faults) — experiments with no {@code podLabels} target an entity ID,
 * database proxy, or LLM endpoint instead of a set of pods, so there is nothing to count and the
 * check passes trivially.
 */
public final class BlastRadiusEnforcer {

  private final PodCounter podCounter;

  public BlastRadiusEnforcer(final PodCounter podCounter) {
    this.podCounter = podCounter;
  }

  public BlastRadiusDecision enforce(final TargetSelector target, final BlastRadius blastRadius) {
    if (target.podLabels().isEmpty()) {
      return BlastRadiusDecision.allowed(0);
    }

    final int matchingPods = podCounter.countMatchingPods(target.namespace(), target.podLabels());
    if (matchingPods == 0) {
      return BlastRadiusDecision.rejected(
          "No pods matched target selector " + target.podLabels(), 0);
    }

    if (blastRadius.scope() == BlastRadiusScope.SINGLE_POD && matchingPods != 1) {
      return BlastRadiusDecision.rejected(
          "SINGLE_POD scope requires exactly 1 matching pod, found " + matchingPods, matchingPods);
    }

    if (blastRadius.maxPods() > 0 && matchingPods > blastRadius.maxPods()) {
      return BlastRadiusDecision.rejected(
          "Matching pods (" + matchingPods + ") exceed maxPods (" + blastRadius.maxPods() + ")",
          matchingPods);
    }

    final int servicePods = podCounter.countServicePods(target.namespace(), target.service());
    if (servicePods > 0 && blastRadius.maxPercentage() > 0) {
      final double percentage = matchingPods * 100.0 / servicePods;
      if (percentage > blastRadius.maxPercentage()) {
        return BlastRadiusDecision.rejected(
            "Matching pods are %.1f%% of service, exceeding maxPercentage %.1f%%"
                .formatted(percentage, blastRadius.maxPercentage()),
            matchingPods);
      }
    }

    return BlastRadiusDecision.allowed(matchingPods);
  }
}

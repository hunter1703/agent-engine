package com.agentengine.chaos.core.engine;

public record BlastRadiusDecision(boolean allowed, String reason, int matchingPods) {

    public static BlastRadiusDecision allowed(final int matchingPods) {
        return new BlastRadiusDecision(true, "", matchingPods);
    }

    public static BlastRadiusDecision rejected(final String reason, final int matchingPods) {
        return new BlastRadiusDecision(false, reason, matchingPods);
    }
}

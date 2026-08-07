package com.agentengine.chaos.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.core.k8s.PodCounter;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlastRadiusEnforcerTest {

    @Test
    void shouldAllowNonPodTargetedExperimentsUnconditionally() {
        final BlastRadiusEnforcer enforcer = new BlastRadiusEnforcer(new FixedPodCounter(0, 0));
        final TargetSelector target = new TargetSelector("agent-engine", "runtime", Map.of(), Optional.of("session-1"));

        final BlastRadiusDecision decision =
                enforcer.enforce(target, new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 25.0));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void shouldRejectWhenNoPodsMatchSelector() {
        final BlastRadiusEnforcer enforcer = new BlastRadiusEnforcer(new FixedPodCounter(0, 10));
        final TargetSelector target = podTarget();

        final BlastRadiusDecision decision =
                enforcer.enforce(target, new BlastRadius(BlastRadiusScope.SERVICE, 2, 25.0));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("No pods matched");
    }

    @Test
    void shouldRejectSinglePodScopeWhenMoreThanOnePodMatches() {
        final BlastRadiusEnforcer enforcer = new BlastRadiusEnforcer(new FixedPodCounter(3, 10));
        final TargetSelector target = podTarget();

        final BlastRadiusDecision decision =
                enforcer.enforce(target, new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("SINGLE_POD");
    }

    @Test
    void shouldRejectWhenMatchingPodsExceedMaxPods() {
        final BlastRadiusEnforcer enforcer = new BlastRadiusEnforcer(new FixedPodCounter(5, 10));
        final TargetSelector target = podTarget();

        final BlastRadiusDecision decision =
                enforcer.enforce(target, new BlastRadius(BlastRadiusScope.SERVICE, 2, 100.0));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("maxPods");
    }

    @Test
    void shouldRejectWhenMatchingPodsExceedMaxPercentageOfService() {
        final BlastRadiusEnforcer enforcer = new BlastRadiusEnforcer(new FixedPodCounter(5, 10));
        final TargetSelector target = podTarget();

        final BlastRadiusDecision decision =
                enforcer.enforce(target, new BlastRadius(BlastRadiusScope.SERVICE, 10, 25.0));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("maxPercentage");
    }

    @Test
    void shouldAllowWhenWithinAllLimits() {
        final BlastRadiusEnforcer enforcer = new BlastRadiusEnforcer(new FixedPodCounter(1, 10));
        final TargetSelector target = podTarget();

        final BlastRadiusDecision decision =
                enforcer.enforce(target, new BlastRadius(BlastRadiusScope.SERVICE, 2, 25.0));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.matchingPods()).isEqualTo(1);
    }

    private static TargetSelector podTarget() {
        return new TargetSelector("agent-engine", "runtime", Map.of("app", "runtime"), Optional.empty());
    }

    private record FixedPodCounter(int matching, int total) implements PodCounter {
        @Override
        public int countMatchingPods(final String namespace, final Map<String, String> podLabels) {
            return matching;
        }

        @Override
        public int countServicePods(final String namespace, final String service) {
            return total;
        }
    }
}

package com.agentengine.chaos.core.evaluation;

import com.agentengine.chaos.api.CriterionFailure;
import com.agentengine.chaos.api.EvaluationResult;
import com.agentengine.chaos.api.SteadyStateMetrics;
import com.agentengine.chaos.api.SuccessCriterion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates an experiment's {@link SuccessCriterion} list against post-recovery metrics. An
 * experiment is {@code PASSED} only when every criterion passes.
 *
 * <p>{@code ZERO_DATA_LOSS} delegates to a caller-supplied {@code dataLossDetected} flag rather
 * than querying the event journal itself — the actual gap detection lives in
 * {@code EventJournalValidator} (chaos/core/validation), which the orchestrating {@code
 * ChaosEngine} runs before calling this evaluator.
 */
public final class SuccessCriteriaEvaluator {

    public EvaluationResult evaluate(
            final List<SuccessCriterion> criteria,
            final SteadyStateMetrics baseline,
            final List<SteadyStateMetrics> duringFault,
            final SteadyStateMetrics postRecovery,
            final Optional<Duration> recoveryTime,
            final boolean dataLossDetected) {
        final List<CriterionFailure> failures = new ArrayList<>();

        for (final SuccessCriterion criterion : criteria) {
            evaluateOne(criterion, postRecovery, recoveryTime, dataLossDetected).ifPresent(failures::add);
        }

        return new EvaluationResult(failures.isEmpty(), failures, baseline, duringFault, postRecovery);
    }

    private Optional<CriterionFailure> evaluateOne(
            final SuccessCriterion criterion,
            final SteadyStateMetrics postRecovery,
            final Optional<Duration> recoveryTime,
            final boolean dataLossDetected) {
        return switch (criterion.type()) {
            case MAX_ERROR_RATE ->
                failIf(postRecovery.errorRate() > criterion.threshold(), criterion, postRecovery.errorRate());
            case MIN_SUCCESS_RATE ->
                failIf(postRecovery.successRate() < criterion.threshold(), criterion, postRecovery.successRate());
            case MAX_LATENCY_P99 ->
                failIf(
                        postRecovery.p99Latency().compareTo(Duration.ofMillis((long) criterion.threshold())) > 0,
                        criterion,
                        postRecovery.p99Latency().toMillis());
            case MAX_RECOVERY_TIME ->
                failIf(
                        recoveryTime.isEmpty()
                                || recoveryTime.get().compareTo(Duration.ofSeconds((long) criterion.threshold())) > 0,
                        criterion,
                        recoveryTime.map(Duration::toSeconds).orElse(Long.MAX_VALUE));
            case ZERO_DATA_LOSS -> failIf(dataLossDetected, criterion, dataLossDetected ? 1.0 : 0.0);
            case UNKNOWN ->
                Optional.of(new CriterionFailure(
                        criterion.type(),
                        criterion.threshold(),
                        Double.NaN,
                        "Unknown criterion type: cannot evaluate"));
        };
    }

    private static Optional<CriterionFailure> failIf(
            final boolean failed, final SuccessCriterion criterion, final double actual) {
        if (!failed) {
            return Optional.empty();
        }
        return Optional.of(
                new CriterionFailure(criterion.type(), criterion.threshold(), actual, criterion.description()));
    }
}

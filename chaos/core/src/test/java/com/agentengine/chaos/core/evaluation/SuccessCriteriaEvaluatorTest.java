package com.agentengine.chaos.core.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.CriterionType;
import com.agentengine.chaos.api.EvaluationResult;
import com.agentengine.chaos.api.SteadyStateMetrics;
import com.agentengine.chaos.api.SuccessCriterion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SuccessCriteriaEvaluatorTest {

  private final SuccessCriteriaEvaluator evaluator = new SuccessCriteriaEvaluator();
  private final SteadyStateMetrics baseline = metrics(0.99, 100, 0.01);

  @Test
  void shouldPassMaxErrorRateWhenBelowThreshold() {
    final EvaluationResult result =
        evaluate(
            List.of(new SuccessCriterion(CriterionType.MAX_ERROR_RATE, 0.05, "low error rate")),
            metrics(0.98, 100, 0.02),
            Optional.empty(),
            false);
    assertThat(result.passed()).isTrue();
  }

  @Test
  void shouldFailMaxErrorRateWhenAboveThreshold() {
    final EvaluationResult result =
        evaluate(
            List.of(new SuccessCriterion(CriterionType.MAX_ERROR_RATE, 0.05, "low error rate")),
            metrics(0.5, 100, 0.5),
            Optional.empty(),
            false);
    assertThat(result.passed()).isFalse();
    assertThat(result.failures()).hasSize(1);
    assertThat(result.failures().get(0).type()).isEqualTo(CriterionType.MAX_ERROR_RATE);
  }

  @Test
  void shouldPassMinSuccessRateWhenAboveThreshold() {
    final EvaluationResult result =
        evaluate(
            List.of(
                new SuccessCriterion(CriterionType.MIN_SUCCESS_RATE, 0.5, "graceful degradation")),
            metrics(0.6, 100, 0.4),
            Optional.empty(),
            false);
    assertThat(result.passed()).isTrue();
  }

  @Test
  void shouldFailMinSuccessRateWhenBelowThreshold() {
    final EvaluationResult result =
        evaluate(
            List.of(
                new SuccessCriterion(CriterionType.MIN_SUCCESS_RATE, 0.5, "graceful degradation")),
            metrics(0.2, 100, 0.8),
            Optional.empty(),
            false);
    assertThat(result.passed()).isFalse();
  }

  @Test
  void shouldPassMaxLatencyP99WhenBelowThresholdMillis() {
    final EvaluationResult result =
        evaluate(
            List.of(new SuccessCriterion(CriterionType.MAX_LATENCY_P99, 500, "fast enough")),
            metrics(0.9, 300, 0.1),
            Optional.empty(),
            false);
    assertThat(result.passed()).isTrue();
  }

  @Test
  void shouldFailMaxLatencyP99WhenAboveThresholdMillis() {
    final EvaluationResult result =
        evaluate(
            List.of(new SuccessCriterion(CriterionType.MAX_LATENCY_P99, 500, "fast enough")),
            metrics(0.9, 3000, 0.1),
            Optional.empty(),
            false);
    assertThat(result.passed()).isFalse();
  }

  @Test
  void shouldPassMaxRecoveryTimeWhenWithinThresholdSeconds() {
    final EvaluationResult result =
        evaluate(
            List.of(new SuccessCriterion(CriterionType.MAX_RECOVERY_TIME, 60, "recovers quickly")),
            metrics(0.9, 100, 0.1),
            Optional.of(Duration.ofSeconds(30)),
            false);
    assertThat(result.passed()).isTrue();
  }

  @Test
  void shouldFailMaxRecoveryTimeWhenRecoveryNeverObserved() {
    final EvaluationResult result =
        evaluate(
            List.of(new SuccessCriterion(CriterionType.MAX_RECOVERY_TIME, 60, "recovers quickly")),
            metrics(0.9, 100, 0.1),
            Optional.empty(),
            false);
    assertThat(result.passed()).isFalse();
  }

  @Test
  void shouldPassZeroDataLossWhenNoGapsDetected() {
    final EvaluationResult result =
        evaluate(
            List.of(new SuccessCriterion(CriterionType.ZERO_DATA_LOSS, 0.0, "no gaps")),
            metrics(0.9, 100, 0.1),
            Optional.empty(),
            false);
    assertThat(result.passed()).isTrue();
  }

  @Test
  void shouldFailZeroDataLossWhenGapsDetected() {
    final EvaluationResult result =
        evaluate(
            List.of(new SuccessCriterion(CriterionType.ZERO_DATA_LOSS, 0.0, "no gaps")),
            metrics(0.9, 100, 0.1),
            Optional.empty(),
            true);
    assertThat(result.passed()).isFalse();
  }

  @Test
  void shouldFailExperimentWhenAnySingleCriterionFailsAmongMany() {
    final EvaluationResult result =
        evaluate(
            List.of(
                new SuccessCriterion(CriterionType.MIN_SUCCESS_RATE, 0.5, "ok"),
                new SuccessCriterion(CriterionType.MAX_ERROR_RATE, 0.01, "strict error budget")),
            metrics(0.9, 100, 0.1),
            Optional.empty(),
            false);
    assertThat(result.passed()).isFalse();
    assertThat(result.failures()).hasSize(1);
    assertThat(result.failures().get(0).type()).isEqualTo(CriterionType.MAX_ERROR_RATE);
  }

  private EvaluationResult evaluate(
      final List<SuccessCriterion> criteria,
      final SteadyStateMetrics postRecovery,
      final Optional<Duration> recoveryTime,
      final boolean dataLossDetected) {
    return evaluator.evaluate(
        criteria, baseline, List.of(baseline), postRecovery, recoveryTime, dataLossDetected);
  }

  private static SteadyStateMetrics metrics(
      final double successRate, final long p99Millis, final double errorRate) {
    return new SteadyStateMetrics(
        successRate,
        Duration.ofMillis(p99Millis / 2),
        Duration.ofMillis(p99Millis - p99Millis / 4),
        Duration.ofMillis(p99Millis),
        errorRate,
        1,
        Duration.ZERO,
        Duration.ZERO,
        0,
        Instant.now());
  }
}

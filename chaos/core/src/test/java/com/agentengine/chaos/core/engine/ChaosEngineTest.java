package com.agentengine.chaos.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.CriterionType;
import com.agentengine.chaos.api.ExperimentDefinition;
import com.agentengine.chaos.api.ExperimentResult;
import com.agentengine.chaos.api.ExperimentStatus;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.SteadyStateMetrics;
import com.agentengine.chaos.api.SuccessCriterion;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.MessageDropParameters;
import com.agentengine.chaos.core.evaluation.SuccessCriteriaEvaluator;
import com.agentengine.chaos.core.injection.FaultInjector;
import com.agentengine.chaos.core.metrics.MetricsCollector;
import com.agentengine.chaos.core.metrics.SteadyStateAnalyzer;
import com.agentengine.chaos.core.validation.DataLossChecker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaosEngineTest {

  private final BlastRadiusEnforcer blastRadiusEnforcer = mock(BlastRadiusEnforcer.class);
  private final MetricsCollector metricsCollector = mock(MetricsCollector.class);
  private final FaultInjector injector = mock(FaultInjector.class);
  private final SteadyStateAnalyzer analyzer = new SteadyStateAnalyzer();
  private final SuccessCriteriaEvaluator evaluator = new SuccessCriteriaEvaluator();

  private ChaosEngine engine;
  private TargetSelector target;
  private ExperimentDefinition experiment;

  @BeforeEach
  void setUp() {
    engine =
        new ChaosEngine(
            List.of(injector),
            blastRadiusEnforcer,
            metricsCollector,
            analyzer,
            evaluator,
            DataLossChecker.noOp(),
            Runnable::run,
            Duration.ofMillis(5),
            10.0,
            false);

    target = new TargetSelector("agent-engine", "agent", Map.of(), Optional.of("session-1"));
    experiment =
        new ExperimentDefinition(
            "test-experiment",
            "desc",
            target,
            FaultType.MESSAGE_DROP,
            new MessageDropParameters(0.5),
            Duration.ofMillis(20),
            new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0),
            List.of(new SuccessCriterion(CriterionType.MIN_SUCCESS_RATE, 0.5, "min success")),
            Duration.ofMillis(10),
            Duration.ofMillis(10),
            Optional.empty(),
            Map.of(),
            false,
            true);

    when(injector.supports(FaultType.MESSAGE_DROP)).thenReturn(true);
  }

  @Test
  void shouldRunFullLifecycleAndPassWhenCriteriaMet() {
    when(blastRadiusEnforcer.enforce(any(), any())).thenReturn(BlastRadiusDecision.allowed(0));
    when(metricsCollector.collectBaseline(eq(target), any(), any()))
        .thenReturn(Optional.of(metrics(0.99)));
    when(injector.injectFault(any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture("fault-1"));
    when(injector.removeFault("fault-1")).thenReturn(CompletableFuture.completedFuture(null));
    when(metricsCollector.pollWindow(eq(target), eq(experiment.duration()), any()))
        .thenReturn(List.of(metrics(0.4)));
    when(metricsCollector.pollWindow(eq(target), eq(experiment.recoveryWindow()), any()))
        .thenReturn(List.of(metrics(0.98)));

    final ExperimentResult result = engine.execute(experiment).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(ExperimentStatus.PASSED);
    assertThat(result.baseline()).isPresent();
    assertThat(result.postRecovery()).isPresent();
    assertThat(result.evaluation()).isPresent();
    assertThat(result.evaluation().get().passed()).isTrue();
    assertThat(result.faultEvents()).hasSize(2);
    verify(injector).removeFault("fault-1");
  }

  @Test
  void shouldFailWhenPostRecoveryCriteriaNotMet() {
    when(blastRadiusEnforcer.enforce(any(), any())).thenReturn(BlastRadiusDecision.allowed(0));
    when(metricsCollector.collectBaseline(eq(target), any(), any()))
        .thenReturn(Optional.of(metrics(0.99)));
    when(injector.injectFault(any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture("fault-1"));
    when(injector.removeFault("fault-1")).thenReturn(CompletableFuture.completedFuture(null));
    when(metricsCollector.pollWindow(eq(target), eq(experiment.duration()), any()))
        .thenReturn(List.of(metrics(0.1)));
    when(metricsCollector.pollWindow(eq(target), eq(experiment.recoveryWindow()), any()))
        .thenReturn(List.of(metrics(0.1)));

    final ExperimentResult result = engine.execute(experiment).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(ExperimentStatus.FAILED);
    assertThat(result.evaluation().get().passed()).isFalse();
  }

  @Test
  void shouldAbortWhenBlastRadiusRejected() {
    when(blastRadiusEnforcer.enforce(any(), any()))
        .thenReturn(BlastRadiusDecision.rejected("too many pods", 5));

    final ExperimentResult result = engine.execute(experiment).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(ExperimentStatus.ABORTED);
    assertThat(result.abortReason()).isPresent();
    assertThat(result.abortReason().orElseThrow()).contains("too many pods");
    assertThat(result.baseline()).isEmpty();
    verify(metricsCollector, never()).collectBaseline(any(), any(), any());
    verify(injector, never()).injectFault(any(), any(), any(), any());
  }

  @Test
  void shouldAbortWhenBaselineCollectionFails() {
    when(blastRadiusEnforcer.enforce(any(), any())).thenReturn(BlastRadiusDecision.allowed(0));
    when(metricsCollector.collectBaseline(eq(target), any(), any())).thenReturn(Optional.empty());

    final ExperimentResult result = engine.execute(experiment).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(ExperimentStatus.ABORTED);
    assertThat(result.abortReason()).isPresent();
    assertThat(result.abortReason().orElseThrow()).contains("Baseline");
    verify(injector, never()).injectFault(any(), any(), any(), any());
  }

  @Test
  void shouldReturnDryRunResultWithoutInjectingFault() {
    when(blastRadiusEnforcer.enforce(any(), any())).thenReturn(BlastRadiusDecision.allowed(0));
    when(metricsCollector.collectBaseline(eq(target), any(), any()))
        .thenReturn(Optional.of(metrics(0.99)));
    final ExperimentDefinition dryRunExperiment =
        new ExperimentDefinition(
            experiment.name(),
            experiment.description(),
            experiment.target(),
            experiment.faultType(),
            experiment.parameters(),
            experiment.duration(),
            experiment.blastRadius(),
            experiment.successCriteria(),
            experiment.observationWindow(),
            experiment.recoveryWindow(),
            experiment.schedule(),
            experiment.labels(),
            true,
            experiment.approved());

    final ExperimentResult result = engine.execute(dryRunExperiment).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(ExperimentStatus.DRY_RUN);
    assertThat(result.baseline()).isPresent();
    verify(injector, never()).injectFault(any(), any(), any(), any());
  }

  @Test
  void shouldThrowWhenNoInjectorSupportsFaultType() {
    when(injector.supports(FaultType.MESSAGE_DROP)).thenReturn(false);

    assertThatThrownBy(() -> engine.execute(experiment))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowWhenProductionExperimentNotApproved() {
    final ChaosEngine productionEngine =
        new ChaosEngine(
            List.of(injector),
            blastRadiusEnforcer,
            metricsCollector,
            analyzer,
            evaluator,
            DataLossChecker.noOp(),
            Runnable::run,
            Duration.ofMillis(5),
            10.0,
            true);
    final ExperimentDefinition unapproved =
        new ExperimentDefinition(
            experiment.name(),
            experiment.description(),
            experiment.target(),
            experiment.faultType(),
            experiment.parameters(),
            experiment.duration(),
            experiment.blastRadius(),
            experiment.successCriteria(),
            experiment.observationWindow(),
            experiment.recoveryWindow(),
            experiment.schedule(),
            experiment.labels(),
            false,
            false);

    assertThatThrownBy(() -> productionEngine.execute(unapproved))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static SteadyStateMetrics metrics(final double successRate) {
    return new SteadyStateMetrics(
        successRate,
        Duration.ofMillis(50),
        Duration.ofMillis(90),
        Duration.ofMillis(120),
        1 - successRate,
        1,
        Duration.ZERO,
        Duration.ZERO,
        0,
        Instant.now());
  }
}

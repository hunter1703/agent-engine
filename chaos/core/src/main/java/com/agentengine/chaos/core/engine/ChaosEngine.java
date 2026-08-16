package com.agentengine.chaos.core.engine;

import com.agentengine.chaos.api.EvaluationResult;
import com.agentengine.chaos.api.ExperimentDefinition;
import com.agentengine.chaos.api.ExperimentResult;
import com.agentengine.chaos.api.ExperimentStatus;
import com.agentengine.chaos.api.FaultEvent;
import com.agentengine.chaos.api.FaultOutcome;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.SteadyStateMetrics;
import com.agentengine.chaos.core.evaluation.SuccessCriteriaEvaluator;
import com.agentengine.chaos.core.injection.FaultInjector;
import com.agentengine.chaos.core.metrics.MetricsCollector;
import com.agentengine.chaos.core.metrics.SteadyStateAnalyzer;
import com.agentengine.chaos.core.validation.DataLossChecker;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates a single experiment's full lifecycle: validate, check blast radius, collect
 * baseline, inject the fault, poll metrics, remove the fault, collect post-recovery metrics,
 * evaluate success criteria. Scheduling, history persistence, and REST wiring live in the {@code
 * ChaosService} implementation that composes this engine (chaos-testing spec Tasks 16, 24) — this
 * class only knows how to run one experiment from start to finish.
 */
public final class ChaosEngine {

  private static final Logger LOG = LoggerFactory.getLogger(ChaosEngine.class);

  private final List<FaultInjector> injectors;
  private final BlastRadiusEnforcer blastRadiusEnforcer;
  private final MetricsCollector metricsCollector;
  private final SteadyStateAnalyzer steadyStateAnalyzer;
  private final SuccessCriteriaEvaluator successCriteriaEvaluator;
  private final DataLossChecker dataLossChecker;
  private final Executor executor;
  private final Duration pollInterval;
  private final double recoveryDeviationThresholdPercent;
  private final boolean productionEnvironment;

  public ChaosEngine(
      final List<FaultInjector> injectors,
      final BlastRadiusEnforcer blastRadiusEnforcer,
      final MetricsCollector metricsCollector,
      final SteadyStateAnalyzer steadyStateAnalyzer,
      final SuccessCriteriaEvaluator successCriteriaEvaluator,
      final DataLossChecker dataLossChecker,
      final Executor executor,
      final Duration pollInterval,
      final double recoveryDeviationThresholdPercent,
      final boolean productionEnvironment) {
    this.injectors = List.copyOf(injectors);
    this.blastRadiusEnforcer = blastRadiusEnforcer;
    this.metricsCollector = metricsCollector;
    this.steadyStateAnalyzer = steadyStateAnalyzer;
    this.successCriteriaEvaluator = successCriteriaEvaluator;
    this.dataLossChecker = dataLossChecker;
    this.executor = executor;
    this.pollInterval = pollInterval;
    this.recoveryDeviationThresholdPercent = recoveryDeviationThresholdPercent;
    this.productionEnvironment = productionEnvironment;
  }

  public CompletionStage<ExperimentResult> execute(final ExperimentDefinition experiment) {
    validate(experiment);
    return CompletableFuture.supplyAsync(() -> runExperiment(experiment), executor);
  }

  private void validate(final ExperimentDefinition experiment) {
    if (experiment.name() == null || experiment.name().isBlank()) {
      throw new IllegalArgumentException("Experiment name is required");
    }
    if (experiment.target() == null) {
      throw new IllegalArgumentException("Experiment target is required");
    }
    if (experiment.faultType() == null || experiment.faultType() == FaultType.UNKNOWN) {
      throw new IllegalArgumentException(
          "Experiment faultType is required and must be a known FaultType");
    }
    if (experiment.duration() == null
        || experiment.duration().isNegative()
        || experiment.duration().isZero()) {
      throw new IllegalArgumentException("Experiment duration must be positive");
    }
    if (experiment.successCriteria() == null || experiment.successCriteria().isEmpty()) {
      throw new IllegalArgumentException("Experiment must declare at least one success criterion");
    }
    if (findInjector(experiment.faultType()).isEmpty()) {
      throw new IllegalArgumentException(
          "No fault injector supports faultType " + experiment.faultType());
    }
    if (productionEnvironment && !experiment.approved()) {
      throw new IllegalArgumentException(
          "Experiment targeting production requires approved=true: " + experiment.name());
    }
  }

  private ExperimentResult runExperiment(final ExperimentDefinition experiment) {
    final String experimentId = UUID.randomUUID().toString();
    final Instant startTime = Instant.now();
    final List<FaultEvent> faultEvents = new ArrayList<>();

    final BlastRadiusDecision decision =
        blastRadiusEnforcer.enforce(experiment.target(), experiment.blastRadius());
    if (!decision.allowed()) {
      LOG.warn("Blast radius rejected experiment {}: {}", experiment.name(), decision.reason());
      return aborted(
          experimentId,
          experiment,
          startTime,
          Optional.empty(),
          List.of(),
          faultEvents,
          decision.reason());
    }

    final Optional<SteadyStateMetrics> baselineOpt =
        metricsCollector.collectBaseline(
            experiment.target(), experiment.observationWindow(), pollInterval);
    if (baselineOpt.isEmpty()) {
      return aborted(
          experimentId,
          experiment,
          startTime,
          Optional.empty(),
          List.of(),
          faultEvents,
          "Baseline metrics collection failed");
    }
    final SteadyStateMetrics baseline = baselineOpt.get();

    if (experiment.dryRun()) {
      LOG.info(
          "Dry-run experiment {}: {} matching pods, baseline collected, no fault injected",
          experiment.name(),
          decision.matchingPods());
      return new ExperimentResult(
          experimentId,
          experiment.name(),
          startTime,
          Optional.of(Instant.now()),
          ExperimentStatus.DRY_RUN,
          Optional.of(baseline),
          List.of(),
          Optional.empty(),
          Optional.empty(),
          List.of(),
          Optional.empty(),
          Optional.empty());
    }

    final FaultInjector injector =
        findInjector(experiment.faultType())
            .orElseThrow(); // validated in validate(); cannot happen here
    final Instant injectedAt = Instant.now();
    final String faultId;
    try {
      faultId =
          injector
              .injectFault(
                  experiment.faultType(),
                  experiment.target(),
                  experiment.parameters(),
                  experiment.blastRadius())
              .toCompletableFuture()
              .join();
    } catch (RuntimeException ex) {
      faultEvents.add(
          new FaultEvent(
              "n/a",
              experiment.faultType(),
              experiment.target(),
              injectedAt,
              Optional.empty(),
              FaultOutcome.INJECTION_FAILED));
      return aborted(
          experimentId,
          experiment,
          startTime,
          Optional.of(baseline),
          List.of(),
          faultEvents,
          "Fault injection failed: " + rootMessage(ex));
    }
    faultEvents.add(
        new FaultEvent(
            faultId,
            experiment.faultType(),
            experiment.target(),
            injectedAt,
            Optional.empty(),
            FaultOutcome.INJECTED));

    final List<SteadyStateMetrics> duringFault =
        metricsCollector.pollWindow(experiment.target(), experiment.duration(), pollInterval);

    final Instant removedAt;
    try {
      injector.removeFault(faultId).toCompletableFuture().join();
      removedAt = Instant.now();
      faultEvents.add(
          new FaultEvent(
              faultId,
              experiment.faultType(),
              experiment.target(),
              injectedAt,
              Optional.of(removedAt),
              FaultOutcome.REMOVED));
    } catch (RuntimeException ex) {
      faultEvents.add(
          new FaultEvent(
              faultId,
              experiment.faultType(),
              experiment.target(),
              injectedAt,
              Optional.empty(),
              FaultOutcome.REMOVAL_FAILED));
      LOG.error(
          "Fault removal failed for experiment {} (faultId={}); manual cleanup required: kubectl / toxiproxy"
              + " cleanup for target {}",
          experiment.name(),
          faultId,
          experiment.target(),
          ex);
      return failed(
          experimentId,
          experiment,
          startTime,
          Optional.of(baseline),
          duringFault,
          faultEvents,
          Optional.empty(),
          "Fault removal failed, requires manual cleanup: " + rootMessage(ex));
    }

    final List<SteadyStateMetrics> recoverySnapshots =
        metricsCollector.pollWindow(experiment.target(), experiment.recoveryWindow(), pollInterval);
    if (recoverySnapshots.isEmpty()) {
      return aborted(
          experimentId,
          experiment,
          startTime,
          Optional.of(baseline),
          duringFault,
          faultEvents,
          "Post-recovery metrics collection failed");
    }
    final SteadyStateMetrics postRecovery = steadyStateAnalyzer.average(recoverySnapshots);
    final Optional<Duration> recoveryTime =
        steadyStateAnalyzer.calculateRecoveryTime(
            baseline, recoverySnapshots, removedAt, recoveryDeviationThresholdPercent);
    final boolean dataLossDetected = dataLossChecker.dataLossDetected(experiment);

    final EvaluationResult evaluation =
        successCriteriaEvaluator.evaluate(
            experiment.successCriteria(),
            baseline,
            duringFault,
            postRecovery,
            recoveryTime,
            dataLossDetected);

    return new ExperimentResult(
        experimentId,
        experiment.name(),
        startTime,
        Optional.of(Instant.now()),
        evaluation.passed() ? ExperimentStatus.PASSED : ExperimentStatus.FAILED,
        Optional.of(baseline),
        duringFault,
        Optional.of(postRecovery),
        Optional.of(evaluation),
        faultEvents,
        recoveryTime,
        Optional.empty());
  }

  private Optional<FaultInjector> findInjector(final FaultType faultType) {
    return injectors.stream().filter(injector -> injector.supports(faultType)).findFirst();
  }

  private static ExperimentResult aborted(
      final String experimentId,
      final ExperimentDefinition experiment,
      final Instant startTime,
      final Optional<SteadyStateMetrics> baseline,
      final List<SteadyStateMetrics> duringFault,
      final List<FaultEvent> faultEvents,
      final String reason) {
    return new ExperimentResult(
        experimentId,
        experiment.name(),
        startTime,
        Optional.of(Instant.now()),
        ExperimentStatus.ABORTED,
        baseline,
        duringFault,
        Optional.empty(),
        Optional.empty(),
        faultEvents,
        Optional.empty(),
        Optional.of(reason));
  }

  private static ExperimentResult failed(
      final String experimentId,
      final ExperimentDefinition experiment,
      final Instant startTime,
      final Optional<SteadyStateMetrics> baseline,
      final List<SteadyStateMetrics> duringFault,
      final List<FaultEvent> faultEvents,
      final Optional<SteadyStateMetrics> postRecovery,
      final String reason) {
    return new ExperimentResult(
        experimentId,
        experiment.name(),
        startTime,
        Optional.of(Instant.now()),
        ExperimentStatus.FAILED,
        baseline,
        duringFault,
        postRecovery,
        Optional.empty(),
        faultEvents,
        Optional.empty(),
        Optional.of(reason));
  }

  private static String rootMessage(final Throwable ex) {
    Throwable cause = ex;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
  }
}

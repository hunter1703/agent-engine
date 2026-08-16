package com.agentengine.chaos.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ExperimentResult(
    String experimentId,
    String experimentName,
    Instant startTime,
    Optional<Instant> endTime,
    ExperimentStatus status,
    Optional<SteadyStateMetrics> baseline,
    List<SteadyStateMetrics> duringFaultMetrics,
    Optional<SteadyStateMetrics> postRecovery,
    Optional<EvaluationResult> evaluation,
    List<FaultEvent> faultEvents,
    Optional<Duration> recoveryTime,
    Optional<String> abortReason) {}

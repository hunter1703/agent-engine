package com.agentengine.chaos.api;

import com.agentengine.chaos.api.fault.FaultParameters;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ExperimentDefinition(
        String name,
        String description,
        TargetSelector target,
        FaultType faultType,
        FaultParameters parameters,
        Duration duration,
        BlastRadius blastRadius,
        List<SuccessCriterion> successCriteria,
        Duration observationWindow,
        Duration recoveryWindow,
        Optional<String> schedule,
        Map<String, String> labels,
        boolean dryRun,
        boolean approved) {}

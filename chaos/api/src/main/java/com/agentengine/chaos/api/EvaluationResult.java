package com.agentengine.chaos.api;

import java.util.List;

public record EvaluationResult(
        boolean passed,
        List<CriterionFailure> failures,
        SteadyStateMetrics baseline,
        List<SteadyStateMetrics> duringFault,
        SteadyStateMetrics postRecovery) {}

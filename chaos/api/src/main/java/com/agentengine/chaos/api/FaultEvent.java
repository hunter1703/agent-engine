package com.agentengine.chaos.api;

import java.time.Instant;
import java.util.Optional;

public record FaultEvent(
    String faultId,
    FaultType faultType,
    TargetSelector targetSelector,
    Instant startTime,
    Optional<Instant> endTime,
    FaultOutcome outcome) {}

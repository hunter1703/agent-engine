package com.agentengine.chaos.api.fault;

import java.time.Duration;
import java.util.Optional;

/**
 * When {@code latency} is empty, the injector applies a full connection block (timeout toxic); when
 * present, it applies the given latency instead of a hard failure.
 */
public record QdrantFailureParameters(Optional<Duration> latency) implements FaultParameters {}

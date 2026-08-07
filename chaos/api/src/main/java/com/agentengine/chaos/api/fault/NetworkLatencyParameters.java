package com.agentengine.chaos.api.fault;

import java.time.Duration;

public record NetworkLatencyParameters(Duration latency) implements FaultParameters {}

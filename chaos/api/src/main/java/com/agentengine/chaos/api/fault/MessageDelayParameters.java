package com.agentengine.chaos.api.fault;

import java.time.Duration;

public record MessageDelayParameters(Duration delay, double percentage)
    implements FaultParameters {}

package com.agentengine.chaos.api.fault;

import java.time.Duration;

public record LlmProviderLatencyParameters(Duration latency) implements FaultParameters {}

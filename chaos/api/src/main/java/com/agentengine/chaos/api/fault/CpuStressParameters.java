package com.agentengine.chaos.api.fault;

public record CpuStressParameters(int cpuPercentage) implements FaultParameters {}

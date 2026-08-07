package com.agentengine.chaos.api.fault;

public record MemoryStressParameters(String memoryLimit) implements FaultParameters {}

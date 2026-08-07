package com.agentengine.chaos.api.fault;

public record DiskStressParameters(String diskIORate) implements FaultParameters {}

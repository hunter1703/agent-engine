package com.agentengine.chaos.api.fault;

public record MessageDropParameters(double dropPercentage) implements FaultParameters {}

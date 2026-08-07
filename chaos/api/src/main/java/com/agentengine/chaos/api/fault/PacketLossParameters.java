package com.agentengine.chaos.api.fault;

public record PacketLossParameters(double lossPercentage) implements FaultParameters {}

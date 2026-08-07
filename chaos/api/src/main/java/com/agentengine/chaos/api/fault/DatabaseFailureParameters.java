package com.agentengine.chaos.api.fault;

public record DatabaseFailureParameters(DatabaseTarget target) implements FaultParameters {}

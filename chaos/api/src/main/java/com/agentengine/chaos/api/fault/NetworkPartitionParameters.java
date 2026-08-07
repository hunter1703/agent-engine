package com.agentengine.chaos.api.fault;

import java.util.List;

public record NetworkPartitionParameters(List<String> blockedServices) implements FaultParameters {}

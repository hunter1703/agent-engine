package com.agentengine.chaos.api;

import java.util.Map;
import java.util.Optional;

public record TargetSelector(
        String namespace, String service, Map<String, String> podLabels, Optional<String> entityId) {}

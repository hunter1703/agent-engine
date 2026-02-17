package com.agentengine.interfaces.rest.dto;

import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@Unremovable
public record ErrorResponse(String code, String message, String traceId) {
}

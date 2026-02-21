package com.agentengine.interfaces.rest.requests;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SchemaLookupRequest(String assetType, String assetId, String agentId) {
}

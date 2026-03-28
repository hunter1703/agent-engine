package com.agentengine.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record SchemaLookupRequest(@NotBlank(message = "Asset type is required") String assetType,
    @NotBlank(message = "Asset id is required") String assetId, String agentId) {
}

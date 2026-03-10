package com.agentengine.interfaces.rest.requests;

public record SchemaLookupRequest(String assetType, String assetId, String agentId) {}

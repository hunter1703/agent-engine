package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON Schema for structured outputs.
 */
public record JsonSchema(
    String name,
    JsonNode schema,
    Boolean strict
) {
}

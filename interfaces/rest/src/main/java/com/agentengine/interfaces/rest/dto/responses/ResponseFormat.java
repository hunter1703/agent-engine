package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response format configuration.
 */
public record ResponseFormat(
    String type,
    @JsonProperty("json_schema") JsonSchema jsonSchema
) {
}

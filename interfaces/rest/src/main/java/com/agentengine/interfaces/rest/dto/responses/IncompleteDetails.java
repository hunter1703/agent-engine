package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Incomplete response details.
 */
public record IncompleteDetails(String reason) {
}

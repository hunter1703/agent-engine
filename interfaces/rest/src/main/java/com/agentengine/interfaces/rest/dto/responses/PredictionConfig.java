package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Prediction configuration for speculative decoding.
 */
public record PredictionConfig(
    String type,
    String content
) {
}

package com.agentengine.interfaces.rest.dto.responses;

/**
 * Prediction configuration for speculative decoding.
 */
public record PredictionConfig(String type, String content) {
}

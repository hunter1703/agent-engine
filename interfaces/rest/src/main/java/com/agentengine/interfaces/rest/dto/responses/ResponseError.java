package com.agentengine.interfaces.rest.dto.responses;

/**
 * Response error details.
 */
public record ResponseError(String code, String message, String param) {
}

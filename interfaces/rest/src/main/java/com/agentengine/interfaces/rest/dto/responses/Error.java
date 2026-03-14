package com.agentengine.interfaces.rest.dto.responses;

/**
 * Error response.
 */
public record Error(String message, String type, String param, String code) {
  public Error(String message, String type) {
    this(message, type, null, null);
  }
}

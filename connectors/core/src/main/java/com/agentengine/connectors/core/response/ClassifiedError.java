package com.agentengine.connectors.core.response;

public record ClassifiedError(String code, String message, boolean retryable) {}

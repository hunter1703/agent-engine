package com.agentengine.connectors.core.config;

public record ErrorMappingRule(Integer statusCode, String body, String bodyContains, String errorCode, String message,
    String messageTemplate, boolean retryable) {
}

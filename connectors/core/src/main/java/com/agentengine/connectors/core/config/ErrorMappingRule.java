package com.agentengine.connectors.core.config;

public record ErrorMappingRule(
    Integer statusCode,
    String bodyJsonPath,
    String bodyContains,
    String errorCode,
    String message,
    String messageTemplate,
    boolean retryable) {}

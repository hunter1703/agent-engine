package com.agentengine.connectors.api.beans;

import java.util.Map;

public record ConnectionSpec(Map<String, Object> schema, Map<String, AuthSpec> authConfigs) {}

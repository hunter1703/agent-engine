package com.agentengine.connectors.api.beans;

import java.util.Map;

public record ConnectorMetadata(
    String appName, String connectorName, String description, Map<String, Object> inputSchema) {}

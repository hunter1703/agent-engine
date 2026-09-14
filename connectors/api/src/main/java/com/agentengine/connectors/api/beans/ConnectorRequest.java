package com.agentengine.connectors.api.beans;

import java.util.Map;

public record ConnectorRequest(
    String appName, String connectorName, String connectionId, Map<String, Object> input) {}

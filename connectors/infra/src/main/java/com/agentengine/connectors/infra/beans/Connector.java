package com.agentengine.connectors.infra.beans;

import java.util.Map;

public record Connector(String name, Map<String, Object> inputSchema, ConnectorSpec spec) {}

package com.agentengine.connectors.api.beans;

import java.util.Map;

public record ConnectorRequest(String name, Map<String, Object> input) {}

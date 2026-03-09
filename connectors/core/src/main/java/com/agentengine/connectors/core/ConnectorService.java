package com.agentengine.connectors.core;

import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import java.util.Map;

public interface ConnectorService {
    ConnectorExecutionResult execute(String connectorId, Map<String, Object> input);
}

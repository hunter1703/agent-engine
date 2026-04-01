package com.agentengine.connectors.core;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import java.util.Optional;

public interface ConnectorRegistry {
    Optional<ConnectorDefinition> getConnector(String id);
}

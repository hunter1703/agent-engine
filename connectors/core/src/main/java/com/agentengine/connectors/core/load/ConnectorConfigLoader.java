package com.agentengine.connectors.core.load;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import java.nio.file.Path;

public interface ConnectorConfigLoader {

  ConnectorDefinition load(Path path);

  ConnectorDefinition load(String rawConfig);
}

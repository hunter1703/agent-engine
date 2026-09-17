package com.agentengine.connectors.infra.builders;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.infra.beans.ConnectorSpec;

public record BuildContext<T>(T spec, ConnectorSpec connectorSpec, Connection connection) {}

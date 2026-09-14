package com.agentengine.connectors.infra.beans;

import com.agentengine.connectors.api.beans.ConnectionSpec;

public record Application(ConnectorSpec connector, ConnectionSpec connection) {}

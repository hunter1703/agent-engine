package com.agentengine.connectors.api.beans;

import java.util.List;

public record ConnectorResult<T>(List<T> result) {}

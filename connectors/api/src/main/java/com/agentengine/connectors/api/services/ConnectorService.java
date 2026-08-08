package com.agentengine.connectors.api.services;

import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import java.util.Map;

public interface ConnectorService {
    <T> ConnectorResult<T> execute(ConnectorRequest request) throws ConnectorException;
}

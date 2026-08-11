package com.agentengine.connectors.api.services;

import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.util.ms.MicroService;

@MicroService("connectors")
public interface ConnectorService {
    <T> ConnectorResult<T> execute(ConnectorRequest request) throws ConnectorException;
}

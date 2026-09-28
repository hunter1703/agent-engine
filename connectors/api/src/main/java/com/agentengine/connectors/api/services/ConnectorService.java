package com.agentengine.connectors.api.services;

import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.util.ms.client.MicroService;
import java.util.List;

@MicroService("connectors")
public interface ConnectorService {
  <T> ConnectorResult<T> execute(ConnectorRequest request) throws ConnectorException;

  ConnectorMetadata describe(String appName, String connectorName) throws ConnectorException;

  List<String> findAllConnectorAppNames();

  List<String> findAllConnectorNames(String appName);
}

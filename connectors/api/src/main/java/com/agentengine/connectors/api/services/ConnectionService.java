package com.agentengine.connectors.api.services;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.ms.client.MicroService;

@MicroService("connectors")
public interface ConnectionService extends ConnectionRefresher {

  <T> ConnectorResult<T> executeConnectorRequest(ConnectorRequest request);

  Connection saveConnection(Connection connection);

  PaginatedResult<Connection> getConnections(String appName, Page page);

  Connection getConnection(String id);

  Connection getDecryptedConnection(String id);

  ConnectionSpec getConnectionSpec(String appName);

  ConnectorMetadata getConnectorMetadata(String appName, String connectorName);
}

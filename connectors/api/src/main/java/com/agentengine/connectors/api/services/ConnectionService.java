package com.agentengine.connectors.api.services;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.ms.client.MicroService;
import java.util.List;

@MicroService("connectors")
public interface ConnectionService {

  <T> ConnectorResult<T> executeConnectorRequest(ConnectorRequest request);

  Connection saveConnection(Connection connection, boolean skipVersion);

  /** The app's connections the caller may read. */
  default PaginatedResult<Connection> getConnections(final String appName, final Page page) {
    return getConnections(appName, page, null, null);
  }

  /**
   * The app's connections the caller may read, holding only {@code includeFields} when set and none
   * of {@code excludeFields}.
   */
  PaginatedResult<Connection> getConnections(
      String appName, Page page, List<String> includeFields, List<String> excludeFields);

  Connection getConnection(String id);

  Connection getDecryptedConnection(String id);

  ConnectionSpec getConnectionSpec(String appName);

  ConnectorMetadata getConnectorMetadata(String appName, String connectorName);
}

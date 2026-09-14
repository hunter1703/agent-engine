package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.connectors.api.services.ConnectionRefresher;
import com.agentengine.connectors.http.beans.HttpRequest;
import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.scripts.templated.Template;
import java.util.HashMap;
import java.util.Map;

public class HeaderAuthDecorator implements AuthDecorator<HttpRequest> {
  private final Template<Map<String, String>> headerTemplate;
  private final ConnectionRefresher connectionRefresher;

  public HeaderAuthDecorator(
      Template<Map<String, String>> headerTemplate, ConnectionRefresher connectionRefresher) {
    this.headerTemplate = headerTemplate;
    this.connectionRefresher = connectionRefresher;
  }

  @Override
  public void decorate(Connection connection, HttpRequest request) {
    connection = connectionRefresher.refreshIfNeeded(connection);
    final Map<String, Object> context = new HashMap<>();
    context.put(ConnectorConstants.INPUT, request.getParameters());

    Map<String, Object> authMap = new HashMap<>();
    if (connection != null) {
      if (connection.getInputs() != null) {
        authMap.put(ConnectorConstants.INPUT, connection.getInputs());
      }
      if (connection.getCredentials() != null) {
        authMap.put(ConnectorConstants.CREDENTIALS, connection.getCredentials());
      }
    }

    context.put(ConnectorConstants.AUTH, authMap);
    final Map<String, String> authHeaders =
        CollectionUtils.nullSafeMap(headerTemplate.getValue(context));
    request.addHeaders(authHeaders);
  }
}

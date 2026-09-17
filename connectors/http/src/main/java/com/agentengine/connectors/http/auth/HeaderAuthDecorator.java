package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.services.ConnectionRefresher;
import com.agentengine.connectors.http.beans.HttpRequest;
import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.connectors.infra.utils.ConnectorUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.scripts.templated.Template;
import java.util.Map;

public class HeaderAuthDecorator implements AuthDecorator<Object, HttpRequest> {
  private final Template<Map<String, String>> headerTemplate;
  private final ConnectionRefresher connectionRefresher;

  public HeaderAuthDecorator(
      Template<Map<String, String>> headerTemplate, ConnectionRefresher connectionRefresher) {
    this.headerTemplate = headerTemplate;
    this.connectionRefresher = connectionRefresher;
  }

  @Override
  public void decorate(Connection connection, Object input, HttpRequest request) {
    connection = connectionRefresher.refreshIfNeeded(connection);
    final Map<String, Object> context =
        ConnectorUtils.buildTemplateContextForAuthDecoration(connection, input);
    final Map<String, String> authHeaders =
        CollectionUtils.nullSafeMap(headerTemplate.getValue(context));
    request.addHeaders(authHeaders);
  }
}

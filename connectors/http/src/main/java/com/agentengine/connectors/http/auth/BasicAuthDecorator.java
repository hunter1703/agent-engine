package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.services.ConnectionRefresher;
import com.agentengine.connectors.http.beans.HttpRequest;
import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.connectors.infra.utils.ConnectorUtils;
import com.agentengine.util.scripts.templated.Template;
import java.util.Base64;
import java.util.Map;

public class BasicAuthDecorator implements AuthDecorator<Object, HttpRequest> {

  private final Template<String> usernameTemplate;
  private final Template<String> passwordTemplate;
  private final ConnectionRefresher connectionRefresher;

  public BasicAuthDecorator(
      Template<String> usernameTemplate,
      Template<String> passwordTemplate,
      ConnectionRefresher connectionRefresher) {
    this.usernameTemplate = usernameTemplate;
    this.passwordTemplate = passwordTemplate;
    this.connectionRefresher = connectionRefresher;
  }

  @Override
  public void decorate(Connection connection, Object input, HttpRequest request) {
    connection = connectionRefresher.refreshIfNeeded(connection);
    final Map<String, Object> context =
        ConnectorUtils.buildTemplateContextForAuthDecoration(connection, input);

    final String username = usernameTemplate == null ? "" : usernameTemplate.getValue(context);
    final String password = passwordTemplate == null ? "" : passwordTemplate.getValue(context);
    final String encoded =
        Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    request.addHeaders(Map.of("Authorization", "Basic " + encoded));
  }
}

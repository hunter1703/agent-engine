package com.agentengine.connectors.infra.utils;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.constants.ConnectorConstants;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the templating context request-decorating auth decorators evaluate their specs against.
 */
public final class ConnectorUtils {

  private ConnectorUtils() {}

  public static Map<String, Object> buildTemplateContextForAuthDecoration(
      final Connection connection, final Object inputs) {
    final Map<String, Object> context = new HashMap<>();
    context.put(ConnectorConstants.INPUT, inputs);

    final Map<String, Object> authMap = new HashMap<>();
    if (connection != null) {
      if (connection.getInputs() != null) {
        authMap.put(ConnectorConstants.INPUT, connection.getInputs());
      }
      if (connection.getCredentials() != null) {
        authMap.put(ConnectorConstants.CREDENTIALS, connection.getCredentials());
      }
    }
    context.put(ConnectorConstants.AUTH, authMap);
    return context;
  }
}

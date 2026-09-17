package com.agentengine.connectors.core.validation;

import com.agentengine.connectors.api.beans.AuthSpec;
import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.core.services.ConnectorRegistry;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.validation.ValidationCollector;
import com.agentengine.util.common.validation.Validator;
import jakarta.inject.Singleton;

@Singleton
public class ConnectionValidator implements Validator<Connection> {
  private final ConnectorRegistry connectorRegistry;

  public ConnectionValidator(final ConnectorRegistry connectorRegistry) {
    this.connectorRegistry = connectorRegistry;
  }

  @Override
  public Class<Connection> targetType() {
    return Connection.class;
  }

  @Override
  public void validate(final Connection connection, final ValidationCollector errors) {
    if (connection == null || errors == null) {
      return;
    }
    if (StringUtils.isBlank(connection.getAppName())) {
      errors.add("appName is required");
      return;
    }
    if (StringUtils.isBlank(connection.getAuthType())) {
      errors.add("authType is required; appName=" + connection.getAppName());
      return;
    }

    final ConnectionSpec spec = connectorRegistry.getConnectionSpec(connection.getAppName());
    final AuthSpec authSpec =
        CollectionUtils.getValueFromMap(
            spec == null ? null : spec.authConfigs(), connection.getAuthType());
    if (authSpec == null) {
      errors.add(
          "authType '"
              + connection.getAuthType()
              + "' is not a configured auth type for appName="
              + connection.getAppName());
    }
  }
}

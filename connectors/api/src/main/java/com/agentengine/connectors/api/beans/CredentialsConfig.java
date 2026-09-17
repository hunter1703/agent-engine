package com.agentengine.connectors.api.beans;

import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.util.common.StringUtils;
import java.util.concurrent.TimeUnit;

public record CredentialsConfig(
    String connectorName,
    String credsExpiryFieldPathTemplate,
    String defaultExpiryTemplate,
    String expiryUnit,
    String expiryType) {

  public String credsExpiryFieldPathTemplate() {
    return StringUtils.isNotEmpty(credsExpiryFieldPathTemplate)
        ? credsExpiryFieldPathTemplate
        : "{{ %s.expires_in }}".formatted(ConnectorConstants.CREDENTIALS);
  }

  public String expiryUnit() {
    return expiryUnit != null ? expiryUnit : TimeUnit.SECONDS.name();
  }

  public String expiryType() {
    return expiryType != null ? expiryType : ConnectorConstants.RELATIVE;
  }
}

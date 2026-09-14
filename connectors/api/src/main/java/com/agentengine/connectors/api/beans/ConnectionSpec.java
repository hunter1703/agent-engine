package com.agentengine.connectors.api.beans;

import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.util.common.StringUtils;
import java.util.Map;

public record ConnectionSpec(
    Map<String, Object> schema,
    String authConnector,
    String refreshConnector,
    String credsExpiryFieldPathTemplate,
    String defaultExpiryTemplate,
    String expiryUnit,
    String expiryType) {

  public String credsExpiryFieldPathTemplate() {
    return StringUtils.isNotEmpty(credsExpiryFieldPathTemplate)
        ? credsExpiryFieldPathTemplate
        : "{{ credentials.expires_in }}";
  }

  public String expiryUnit() {
    return expiryUnit != null ? expiryUnit : "SECONDS";
  }

  public String expiryType() {
    return expiryType != null ? expiryType : ConnectorConstants.RELATIVE;
  }
}

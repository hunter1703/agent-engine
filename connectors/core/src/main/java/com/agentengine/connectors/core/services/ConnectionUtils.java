package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.api.beans.CredentialsConfig;
import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.SchemaUtils;
import com.agentengine.util.crypto.EncryptionService;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectionUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ConnectionUtils.class);

  private ConnectionUtils() {}

  public static Long getCredentialsExpiry(
      final Map<String, Object> fetchedCredentials,
      final CredentialsConfig credentialsConfig,
      final Map<String, Object> connectionInputs) {
    final Map<String, Object> contextParams =
        Map.of(
            ConnectorConstants.CONNECTION_INPUT,
            connectionInputs,
            ConnectorConstants.CREDENTIALS,
            fetchedCredentials);
    final Template<Object> expiryTemplate =
        TemplateUtils.buildStringTemplate(credentialsConfig.credsExpiryFieldPathTemplate());
    Long expiry = parseExpiry(expiryTemplate.getValue(contextParams));
    final String expiryUnit = credentialsConfig.expiryUnit();

    if (expiry == null) {
      final Template<Object> defaultExpiryTemplate =
          TemplateUtils.buildStringTemplate(credentialsConfig.defaultExpiryTemplate());
      expiry = parseExpiry(defaultExpiryTemplate.getValue(contextParams));
    }
    return getAbsoluteExpiry(
        expiry,
        TimeUnit.valueOf(expiryUnit.toUpperCase(Locale.ROOT)),
        credentialsConfig.expiryType());
  }

  public static Long parseExpiry(Object value) {
    switch (value) {
      case null -> {
        return null;
      }
      case Number number -> {
        return number.longValue();
      }
      case String str -> {
        try {
          return Long.parseLong(str);
        } catch (NumberFormatException e) {
          LOG.warn("Failed to parse expiry value: {}", str);
          return null;
        }
      }
      default -> {}
    }
    return null;
  }

  public static Long getAbsoluteExpiry(final Long expiry, final TimeUnit unit, final String type) {
    if (expiry == null) {
      return null;
    }
    if (ConnectorConstants.RELATIVE.equalsIgnoreCase(type)) {
      return System.currentTimeMillis() + unit.toMillis(expiry);
    } else {
      return unit.toMillis(expiry);
    }
  }

  public static void encryptSensitiveInputs(
      Connection connection, ConnectionSpec spec, EncryptionService encryptionService) {
    if (CollectionUtils.isEmpty(connection.getInputs())
        || !encryptionService.isEncryptionEnabled()) {
      return;
    }
    if (spec == null || CollectionUtils.isEmpty(spec.schema())) {
      return;
    }

    @SuppressWarnings("unchecked")
    final Map<String, Object> newInputs =
        (Map<String, Object>)
            SchemaUtils.walk(
                spec.schema(),
                connection.getInputs(),
                (jsonPointer, schemaNode, dataNode) -> {
                  if (Boolean.TRUE.equals(
                          CollectionUtils.getBooleanValueFromMap(schemaNode, "sensitive"))
                      && dataNode instanceof String strData) {
                    return encryptionService.encrypt(strData);
                  }
                  return dataNode;
                });
    connection.setInputs(newInputs);
  }

  public static void decryptSensitiveInputs(
      Connection connection, EncryptionService encryptionService) {
    if (connection.getInputs() == null || !encryptionService.isEncryptionEnabled()) return;

    @SuppressWarnings("unchecked")
    Map<String, Object> newInputs =
        (Map<String, Object>)
            CollectionUtils.walk(
                connection.getInputs(),
                (jsonPointer, dataNode) -> {
                  if (dataNode instanceof String str && encryptionService.isEncrypted(str)) {
                    return encryptionService.decrypt(str);
                  }
                  return dataNode;
                });
    connection.setInputs(newInputs);
  }
}

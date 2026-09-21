package com.agentengine.util.infra;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.config.ApplicationConfig;
import java.util.Locale;

public enum ServerType {
  MONGO_SERVER,
  SQL_SERVER,
  VECTOR_SERVER,
  CLOUDSTORAGE_SERVER,
  ENCRYPTION_KEY,
  MICROSERVICE_SERVER,
  UNKNOWN;

  private static final String DEFAULT_SERVER_PROPERTY_PREFIX = "infra.default-server.";

  public String defaultServerId(final ApplicationConfig applicationConfig) {
    final String property = DEFAULT_SERVER_PROPERTY_PREFIX + name();
    final String defaultServerId = applicationConfig.getString(property);
    if (StringUtils.isBlank(defaultServerId)) {
      throw new IllegalStateException("No default server for '" + name() + "': set " + property);
    }
    return defaultServerId;
  }

  public static ServerType valueOfOrDefault(final String value) {
    if (value == null) {
      return UNKNOWN;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}

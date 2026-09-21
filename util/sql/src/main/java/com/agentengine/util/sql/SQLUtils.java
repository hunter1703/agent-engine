package com.agentengine.util.sql;

import com.agentengine.util.infra.ClientType;
import com.agentengine.util.common.StringUtils;
import java.util.regex.Pattern;

public final class SQLUtils {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");

  private SQLUtils() {}

  public static String quoteIdentifier(final String name) {
    if (name == null || !IDENTIFIER.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid SQL identifier '" + name + "'");
    }
    return StringUtils.wrapInQuotes(name);
  }

  public static String clientId(final String store, final Integer customerId) {
    return ClientType.SQL_CLIENT + ":" + store + ":" + customerId;
  }

  public static SQLClientInfraConfig clientConfig(
      final String store, final Integer customerId, final String serverId) {
    final SQLClientInfraConfig clientConfig = new SQLClientInfraConfig();
    clientConfig.setStore(store);
    clientConfig.setCustomerId(customerId);
    clientConfig.setServerId(serverId);
    return clientConfig;
  }
}

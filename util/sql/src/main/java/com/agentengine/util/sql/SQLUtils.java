package com.agentengine.util.sql;

public final class SQLUtils {

  private SQLUtils() {}

  public static String clientId(final String store, final Integer customerId) {
    return SQLClientInfraConfig.TYPE + ":" + store + ":" + customerId;
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

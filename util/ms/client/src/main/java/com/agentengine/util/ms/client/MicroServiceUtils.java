package com.agentengine.util.ms.client;

public final class MicroServiceUtils {

  private MicroServiceUtils() {}

  public static String clientId(final Integer customerId, final String service) {
    return MicroServiceClientInfraConfig.TYPE + ":" + service + ":" + customerId;
  }

  public static MicroServiceClientInfraConfig clientConfig(
      final Integer customerId, final String service, final String serverId) {
    final MicroServiceClientInfraConfig clientConfig = new MicroServiceClientInfraConfig();
    clientConfig.setCustomerId(customerId);
    clientConfig.setService(service);
    clientConfig.setServerId(serverId);
    return clientConfig;
  }
}

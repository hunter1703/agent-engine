package com.agentengine.util.ms.client;

import com.agentengine.util.infra.ClientType;

public final class MicroServiceUtils {

  private MicroServiceUtils() {}

  public static String defaultServerId(final String service) {
    return service + "-default";
  }

  public static String clientId(final Integer customerId, final String service) {
    return ClientType.MICROSERVICE_CLIENT + ":" + service + ":" + customerId;
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

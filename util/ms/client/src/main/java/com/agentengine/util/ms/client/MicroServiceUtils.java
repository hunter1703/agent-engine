package com.agentengine.util.ms.client;

public final class MicroServiceUtils {

  private MicroServiceUtils() {}

  public static String clientId(final Integer customerId, final String service) {
    return MicroServiceClientInfraConfig.TYPE + ":" + service + ":" + customerId;
  }
}

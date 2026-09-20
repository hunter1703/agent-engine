package com.agentengine.util.crypto;

public final class EncryptionUtils {

  private EncryptionUtils() {}

  public static String clientId(final Integer customerId) {
    return EncryptionClientInfraConfig.TYPE + ":" + customerId;
  }

  public static EncryptionClientInfraConfig clientConfig(
      final Integer customerId, final String keyId) {
    final EncryptionClientInfraConfig clientConfig = new EncryptionClientInfraConfig();
    clientConfig.setCustomerId(customerId);
    clientConfig.setServerId(keyId);
    return clientConfig;
  }
}

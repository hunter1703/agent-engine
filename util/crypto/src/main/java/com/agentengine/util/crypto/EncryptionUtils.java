package com.agentengine.util.crypto;

import com.agentengine.util.infra.ClientType;

public final class EncryptionUtils {

  private EncryptionUtils() {}

  public static String clientId(final Integer customerId) {
    return ClientType.ENCRYPTION_CLIENT + ":" + customerId;
  }

  public static EncryptionClientInfraConfig clientConfig(
      final Integer customerId, final String keyId) {
    final EncryptionClientInfraConfig clientConfig = new EncryptionClientInfraConfig();
    clientConfig.setCustomerId(customerId);
    clientConfig.setServerId(keyId);
    return clientConfig;
  }
}

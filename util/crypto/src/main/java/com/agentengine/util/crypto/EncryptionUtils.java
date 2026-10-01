package com.agentengine.util.crypto;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.util.infra.ClientType;

public final class EncryptionUtils {

  private EncryptionUtils() {}

  public static String clientId(final String customerId) {
    return ClientType.ENCRYPTION_CLIENT + ID_SEPARATOR + customerId;
  }

  public static EncryptionClientInfraConfig clientConfig(
      final String customerId, final String keyId) {
    final EncryptionClientInfraConfig clientConfig = new EncryptionClientInfraConfig();
    clientConfig.setCustomerId(customerId);
    clientConfig.setServerId(keyId);
    return clientConfig;
  }
}

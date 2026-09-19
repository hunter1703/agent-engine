package com.agentengine.util.crypto;

public final class EncryptionUtils {

  private EncryptionUtils() {}

  public static String clientId(final Integer customerId) {
    return EncryptionClientInfraConfig.TYPE + ":" + customerId;
  }
}

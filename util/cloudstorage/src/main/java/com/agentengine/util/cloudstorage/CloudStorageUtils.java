package com.agentengine.util.cloudstorage;

public final class CloudStorageUtils {

  private CloudStorageUtils() {}

  public static String clientId(final Integer customerId) {
    return CloudStorageClientInfraConfig.TYPE + ":" + customerId;
  }
}

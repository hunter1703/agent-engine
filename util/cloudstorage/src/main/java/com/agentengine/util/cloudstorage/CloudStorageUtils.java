package com.agentengine.util.cloudstorage;

import com.agentengine.util.infra.ClientType;

public final class CloudStorageUtils {

  private CloudStorageUtils() {}

  public static String clientId(final Integer customerId) {
    return ClientType.CLOUDSTORAGE_CLIENT + ":" + customerId;
  }

  public static CloudStorageClientInfraConfig clientConfig(
      final Integer customerId, final String serverId, final String bucket) {
    final CloudStorageClientInfraConfig clientConfig = new CloudStorageClientInfraConfig();
    clientConfig.setCustomerId(customerId);
    clientConfig.setServerId(serverId);
    clientConfig.setBucket(bucket);
    return clientConfig;
  }

  /** The bucket of a customer whose client config does not name one. */
  public static String defaultBucket(final Integer customerId) {
    return "agentengine-" + customerId;
  }
}

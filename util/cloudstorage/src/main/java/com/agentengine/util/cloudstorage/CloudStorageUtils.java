package com.agentengine.util.cloudstorage;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.util.infra.ClientType;

public final class CloudStorageUtils {

  private CloudStorageUtils() {}

  public static String clientId(final String customerId) {
    return ClientType.CLOUDSTORAGE_CLIENT + ID_SEPARATOR + customerId;
  }

  public static CloudStorageClientInfraConfig clientConfig(
      final String customerId, final String serverId, final String bucket) {
    final CloudStorageClientInfraConfig clientConfig = new CloudStorageClientInfraConfig();
    clientConfig.setCustomerId(customerId);
    clientConfig.setServerId(serverId);
    clientConfig.setBucket(bucket);
    return clientConfig;
  }

  /** The bucket of a customer whose client config does not name one. */
  public static String defaultBucket(final String customerId) {
    return "agentengine-" + customerId;
  }
}

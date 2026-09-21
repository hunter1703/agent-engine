package com.agentengine.util.cloudstorage;

import com.agentengine.util.infra.ClientType;
import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/** A customer's object storage: the server it lives on, in the customer's own bucket. */
@BsonDiscriminator(value = "com.agentengine.util.cloudstorage.CloudStorageClientInfraConfig")
public class CloudStorageClientInfraConfig extends InfraConfig {
  private String bucket;

  public CloudStorageClientInfraConfig() {
    setType(ClientType.CLOUDSTORAGE_CLIENT.name());
  }

  @Override
  public String getId() {
    return CloudStorageUtils.clientId(getCustomerId());
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }
}

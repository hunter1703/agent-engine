package com.agentengine.util.crypto;

import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.crypto.EncryptionClientInfraConfig")
public class EncryptionClientInfraConfig extends InfraConfig {
  public static final String TYPE = "ENCRYPTION_CLIENT";

  public EncryptionClientInfraConfig() {
    setType(TYPE);
  }

  @Override
  public String getId() {
    return EncryptionUtils.clientId(getCustomerId());
  }
}

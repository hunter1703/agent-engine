package com.agentengine.util.crypto;

import com.agentengine.util.infra.ClientType;
import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.crypto.EncryptionClientInfraConfig")
public class EncryptionClientInfraConfig extends InfraConfig {
  public EncryptionClientInfraConfig() {
    setType(ClientType.ENCRYPTION_CLIENT.name());
  }

  @Override
  public String getId() {
    return EncryptionUtils.clientId(getCustomerId());
  }
}

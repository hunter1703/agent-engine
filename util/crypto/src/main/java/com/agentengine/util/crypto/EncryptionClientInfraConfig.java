package com.agentengine.util.crypto;

import com.agentengine.util.infra.InfraClientConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.crypto.EncryptionClientInfraConfig")
public class EncryptionClientInfraConfig extends InfraClientConfig {
  public static final String CATEGORY = "ENCRYPTION";
  public static final String DEFAULT_TYPE = "DEFAULT";
}

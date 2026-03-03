package com.agentengine.engine.infra;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "encryption")
public class EncryptionInfraConfig extends InfraConfig {
  public static final String TYPE = "encryption";
  
  private String key;

  public EncryptionInfraConfig() {
    super(TYPE);
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }
}

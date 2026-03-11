package com.agentengine.util.mongodb.infra;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "encryption")
public class EncryptionInfraConfig extends InfraConfig {
  public static final String TYPE = "encryption";

  /** MongoDB document field name for the encryption key; matches the shell script upsert. */
  public static final String KEY_FIELD = "key";

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

package com.agentengine.util.crypto;

import com.agentengine.util.infra.InfraConfig;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.crypto.EncryptionKeyInfraConfig")
public class EncryptionKeyInfraConfig extends InfraConfig {
  public static final String TYPE = "ENCRYPTION_KEY";

  private String keyId;
  private String provider = Provider.UNKNOWN.name();
  private String key;
  private String vaultKeyId;
  private String cryptoEndpoint;

  public EncryptionKeyInfraConfig() {
    setType(TYPE);
  }

  @Override
  public String getId() {
    return TYPE + ":" + keyId;
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(final String keyId) {
    this.keyId = keyId;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(final String provider) {
    this.provider = provider;
  }

  public Provider providerType() {
    return Provider.valueOfOrUnknown(provider);
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public String getVaultKeyId() {
    return vaultKeyId;
  }

  public void setVaultKeyId(final String vaultKeyId) {
    this.vaultKeyId = vaultKeyId;
  }

  public String getCryptoEndpoint() {
    return cryptoEndpoint;
  }

  public void setCryptoEndpoint(final String cryptoEndpoint) {
    this.cryptoEndpoint = cryptoEndpoint;
  }

  public enum Provider {
    KEY,
    OCI_KMS,
    UNKNOWN;

    public static Provider valueOfOrUnknown(final String value) {
      if (value == null) {
        return UNKNOWN;
      }
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (final IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }
}

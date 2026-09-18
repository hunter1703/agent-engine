package com.agentengine.util.crypto;

import com.agentengine.util.infra.InfraConfig;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.crypto.EncryptionKeyInfraConfig")
public class EncryptionKeyInfraConfig extends InfraConfig {
  public static final String TYPE = "KEY";
  public static final String CATEGORY = "ENCRYPTION";

  private long keyVersion;
  private String provider = Provider.UNKNOWN.name();

  /** {@link Provider#KEY}: the base64 AES-256 key. */
  private String key;

  private String keyId;
  private String cryptoEndpoint;
  private String wrappedKey;

  public long getKeyVersion() {
    return keyVersion;
  }

  public void setKeyVersion(final long keyVersion) {
    this.keyVersion = keyVersion;
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

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(final String keyId) {
    this.keyId = keyId;
  }

  public String getCryptoEndpoint() {
    return cryptoEndpoint;
  }

  public void setCryptoEndpoint(final String cryptoEndpoint) {
    this.cryptoEndpoint = cryptoEndpoint;
  }

  public String getWrappedKey() {
    return wrappedKey;
  }

  public void setWrappedKey(final String wrappedKey) {
    this.wrappedKey = wrappedKey;
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

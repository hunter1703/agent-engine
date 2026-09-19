package com.agentengine.util.cloudstorage;

import com.agentengine.util.common.Secure;
import com.agentengine.util.infra.InfraConfig;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.cloudstorage.CloudStorageServerInfraConfig")
public class CloudStorageServerInfraConfig extends InfraConfig {
  public static final String TYPE = "CLOUDSTORAGE_SERVER";

  private String provider = Provider.S3.name();
  private String region = "us-east-1";
  private String endpointUrl = "http://localhost:4566";
  private String accessKeyId = "test";
  @Secure private String secretAccessKey = "test";
  private boolean pathStyleAccess = true;
  // AWS SDK's own default. Some S3-compatible providers (e.g. OCI Object Storage) return
  // "501 AWS chunked encoding not supported" for the SDK's default streaming-signed uploads
  // and need this set to false.
  private boolean chunkedEncodingEnabled = true;
  private String namespace;
  private boolean useInstancePrincipal = false;
  private String tenantId;
  private String userId;
  private String fingerprint;
  @Secure private String privateKey;
  @Secure private String passPhrase;

  public CloudStorageServerInfraConfig() {
    setType(TYPE);
  }

  @Override
  public String getId() {
    return TYPE + ":" + getServerId();
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

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public String getEndpointUrl() {
    return endpointUrl;
  }

  public void setEndpointUrl(final String endpointUrl) {
    this.endpointUrl = endpointUrl;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(final String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  public void setSecretAccessKey(final String secretAccessKey) {
    this.secretAccessKey = secretAccessKey;
  }

  public boolean isPathStyleAccess() {
    return pathStyleAccess;
  }

  public void setPathStyleAccess(final boolean pathStyleAccess) {
    this.pathStyleAccess = pathStyleAccess;
  }

  public boolean isChunkedEncodingEnabled() {
    return chunkedEncodingEnabled;
  }

  public void setChunkedEncodingEnabled(final boolean chunkedEncodingEnabled) {
    this.chunkedEncodingEnabled = chunkedEncodingEnabled;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(final String namespace) {
    this.namespace = namespace;
  }

  public boolean isUseInstancePrincipal() {
    return useInstancePrincipal;
  }

  public void setUseInstancePrincipal(final boolean useInstancePrincipal) {
    this.useInstancePrincipal = useInstancePrincipal;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(final String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(final String privateKey) {
    this.privateKey = privateKey;
  }

  public String getPassPhrase() {
    return passPhrase;
  }

  public void setPassPhrase(final String passPhrase) {
    this.passPhrase = passPhrase;
  }

  public enum Provider {
    S3,
    ORACLE,
    UNKNOWN;

    public static Provider valueOfOrUnknown(final String value) {
      try {
        return Provider.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (final IllegalArgumentException | NullPointerException e) {
        return UNKNOWN;
      }
    }
  }
}

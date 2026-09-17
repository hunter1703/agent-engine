package com.agentengine.util.cloudstorage;

import com.agentengine.util.common.Secure;
import com.agentengine.util.mongodb.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.cloudstorage.CloudStorageInfraConfig")
public class CloudStorageInfraConfig extends InfraConfig {
  public static final String TYPE = "CLOUDSTORAGE";
  public static final String CATEGORY = "CLOUDSTORAGE";
  public static final String CONFIG_ID = "default";

  private String endpointUrl = "http://localhost:4566";
  private String region = "us-east-1";
  private String accessKeyId = "test";
  @Secure private String secretAccessKey = "test";
  private String defaultBucket = "agent-assets";
  private boolean pathStyleAccess = true;
  // AWS SDK's own default. Some S3-compatible providers (e.g. OCI Object Storage) return
  // "501 AWS chunked encoding not supported" for the SDK's default streaming-signed uploads
  // and need this set to false.
  private boolean chunkedEncodingEnabled = true;

  public String getEndpointUrl() {
    return endpointUrl;
  }

  public void setEndpointUrl(final String endpointUrl) {
    this.endpointUrl = endpointUrl;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
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

  public String getDefaultBucket() {
    return defaultBucket;
  }

  public void setDefaultBucket(final String defaultBucket) {
    this.defaultBucket = defaultBucket;
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
}

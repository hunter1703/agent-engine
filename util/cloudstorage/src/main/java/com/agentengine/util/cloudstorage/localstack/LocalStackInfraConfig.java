package com.agentengine.util.cloudstorage.localstack;

import com.agentengine.util.mongodb.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.cloudstorage.localstack.LocalStackInfraConfig")
public class LocalStackInfraConfig extends InfraConfig {
    public static final String TYPE = "localstack";
    public static final String CATEGORY = "LOCALSTACK";
    public static final String CONFIG_ID = "default";

    private String endpointUrl = "http://localhost:4566";
    private String region = "us-east-1";
    private String accessKeyId = "test";
    private String secretAccessKey = "test";
    private String defaultBucket = "agent-assets";

    public LocalStackInfraConfig() {
        super(TYPE);
    }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(final String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getRegion() { return region; }
    public void setRegion(final String region) { this.region = region; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(final String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(final String secretAccessKey) { this.secretAccessKey = secretAccessKey; }

    public String getDefaultBucket() { return defaultBucket; }
    public void setDefaultBucket(final String defaultBucket) { this.defaultBucket = defaultBucket; }
}

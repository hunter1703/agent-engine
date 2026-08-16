package com.agentengine.scheduler.core.config;

import com.agentengine.util.mongodb.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/**
 * Quota for one job tag, stored as infra config under id {@code SCHEDULER:TAG_CONFIG:<tag>}.
 *
 * <p>A tag with no config is unlimited, so quotas are opt-in: capping a tag is a config change
 * rather than a code change, and a job that introduces a new tag keeps running until someone decides
 * to bound it.
 */
@BsonDiscriminator(value = "com.agentengine.scheduler.core.config.JobTagConfig")
public class JobTagConfig extends InfraConfig {

    public static final String CATEGORY = "SCHEDULER";
    public static final String TYPE = "TAG_CONFIG";

    private int maxConcurrent;

    /** Maximum triggers of this tag in flight across the cluster. */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(final int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }
}

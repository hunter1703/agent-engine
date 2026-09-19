package com.agentengine.scheduler.core.config;

import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.scheduler.core.config.JobTagSettings")
public class JobTagSettings extends InfraConfig {

  public static final String TYPE = "JOB_TAG_SETTINGS";

  private int maxConcurrent;

  /** Maximum triggers of this tag in flight across the cluster. */
  public int getMaxConcurrent() {
    return maxConcurrent;
  }

  public void setMaxConcurrent(final int maxConcurrent) {
    this.maxConcurrent = maxConcurrent;
  }
}

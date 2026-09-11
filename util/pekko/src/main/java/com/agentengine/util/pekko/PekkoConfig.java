package com.agentengine.util.pekko;

import com.agentengine.util.mongodb.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.pekko.PekkoConfig")
public class PekkoConfig extends InfraConfig {
  public static final String TYPE = "PEKKO";
  public static final String CATEGORY = "PEKKO";
  public static final String CONFIG_ID = "default";

  private String clusterName;

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(final String clusterName) {
    this.clusterName = clusterName;
  }
}

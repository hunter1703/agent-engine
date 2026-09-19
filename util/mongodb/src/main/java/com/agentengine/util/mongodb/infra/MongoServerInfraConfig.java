package com.agentengine.util.mongodb.infra;

import com.agentengine.util.common.Secure;
import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/** A MongoDB deployment that holds customers' databases. */
@BsonDiscriminator(value = "com.agentengine.util.mongodb.infra.MongoServerInfraConfig")
public class MongoServerInfraConfig extends InfraConfig {
  public static final String TYPE = "MONGO_SERVER";

  @Secure
  private String uri;

  public MongoServerInfraConfig() {
    setType(TYPE);
  }

  public static String id(final String serverId) {
    return TYPE + ":" + serverId;
  }

  @Override
  public String getId() {
    return id(getServerId());
  }

  public String getUri() {
    return uri;
  }

  public void setUri(final String uri) {
    this.uri = uri;
  }
}

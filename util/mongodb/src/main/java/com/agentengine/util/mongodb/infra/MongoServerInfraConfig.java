package com.agentengine.util.mongodb.infra;

import com.agentengine.util.infra.ServerType;
import com.agentengine.util.common.Secure;
import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/** A MongoDB deployment that holds customers' databases. */
@BsonDiscriminator(value = "com.agentengine.util.mongodb.infra.MongoServerInfraConfig")
public class MongoServerInfraConfig extends InfraConfig {
  @Secure
  private String uri;

  public MongoServerInfraConfig() {
    setType(ServerType.MONGO_SERVER.name());
  }

  public static String id(final String serverId) {
    return ServerType.MONGO_SERVER + ":" + serverId;
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

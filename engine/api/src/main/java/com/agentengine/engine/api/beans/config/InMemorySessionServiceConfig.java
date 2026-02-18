package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("memory")
@BsonDiscriminator(value = "memory")
public class InMemorySessionServiceConfig extends SessionServiceConfig {

  public InMemorySessionServiceConfig() {
    super(SessionServiceType.MEMORY);
  }
}

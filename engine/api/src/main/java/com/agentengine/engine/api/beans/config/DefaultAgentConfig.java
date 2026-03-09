package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("default")
@BsonDiscriminator(value = "default")
public class DefaultAgentConfig extends BaseAgentConfig {
  public DefaultAgentConfig() {
    super(AgentType.DEFAULT);
  }
}

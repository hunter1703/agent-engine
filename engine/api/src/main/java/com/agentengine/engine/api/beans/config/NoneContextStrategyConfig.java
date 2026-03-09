package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("none")
@BsonDiscriminator(value = "none")
public class NoneContextStrategyConfig extends ContextStrategyConfig {
  public NoneContextStrategyConfig() {
    super(ContextStrategyType.NONE);
  }
}

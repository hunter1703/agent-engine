package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@Schema(
    oneOf = {LastNContextManagerConfig.class},
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "last_n", schema = LastNContextManagerConfig.class)
    }
)
@JSONType(typeKey = "type", seeAlso = {LastNContextManagerConfig.class})
@BsonDiscriminator(key = "type")
public abstract class ContextManagerConfig implements Config {
  private String type;

  protected ContextManagerConfig(final ContextType contextType) {
    this.type = contextType.name().toLowerCase();
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public enum ContextType {
    SUMMARIZE, LAST_N
  }
}

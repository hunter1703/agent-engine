package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@Schema(oneOf = {InMemorySessionServiceConfig.class,
    MongoSessionServiceConfig.class}, discriminatorProperty = "type", discriminatorMapping = {
        @DiscriminatorMapping(value = "memory", schema = InMemorySessionServiceConfig.class),
        @DiscriminatorMapping(value = "mongodb", schema = MongoSessionServiceConfig.class)})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = InMemorySessionServiceConfig.class, name = "memory"),
    @JsonSubTypes.Type(value = MongoSessionServiceConfig.class, name = "mongodb")})
@BsonDiscriminator(key = "type")
public abstract class SessionServiceConfig implements Config {
  private String type;

  protected SessionServiceConfig(final SessionServiceType sessionServiceType) {
    this.type = sessionServiceType.name().toLowerCase();
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public enum SessionServiceType {
    MEMORY, MONGODB
  }
}

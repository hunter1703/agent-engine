package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
    oneOf = {
      CompactionContextStrategyConfig.class,
      LastNContextStrategyConfig.class,
      NoneContextStrategyConfig.class
    },
    discriminatorProperty = "type",
    discriminatorMapping = {
      @DiscriminatorMapping(value = "compaction", schema = CompactionContextStrategyConfig.class),
      @DiscriminatorMapping(value = "last_n", schema = LastNContextStrategyConfig.class),
      @DiscriminatorMapping(value = "none", schema = NoneContextStrategyConfig.class)
    })
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CompactionContextStrategyConfig.class, name = "compaction"),
  @JsonSubTypes.Type(value = LastNContextStrategyConfig.class, name = "last_n"),
  @JsonSubTypes.Type(value = NoneContextStrategyConfig.class, name = "none")
})
@BsonDiscriminator(key = "type")
public abstract class ContextStrategyConfig {
  private String type;

  protected ContextStrategyConfig(final ContextStrategyType type) {
    this.type = type.name().toLowerCase(Locale.ROOT);
  }

  protected ContextStrategyConfig() {}

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public enum ContextStrategyType {
    UNKNOWN,
    COMPACTION,
    LAST_N,
    NONE;

    public static ContextStrategyType valueOfOrDefault(final String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      try {
        return ContextStrategyType.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }
}

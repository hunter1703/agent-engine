package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        oneOf = {CompactionContextStrategyConfig.class, LastNContextStrategyConfig.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "COMPACTION", schema = CompactionContextStrategyConfig.class),
            @DiscriminatorMapping(value = "LAST_N", schema = LastNContextStrategyConfig.class)
        })
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CompactionContextStrategyConfig.class, name = "COMPACTION"),
    @JsonSubTypes.Type(value = LastNContextStrategyConfig.class, name = "LAST_N")
})
@BsonDiscriminator(key = "type")
public abstract class ContextStrategyConfig {
    @UiField(label = "Strategy Type", order = 10)
    @UiSelect(enumType = ContextStrategyType.class)
    private String type;

    protected ContextStrategyConfig(final ContextStrategyType type) {
        this.type = type.type();
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
        LAST_N;

        public String type() {
            return name();
        }

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

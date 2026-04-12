package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("LAST_N")
@BsonDiscriminator(value = "com.agentengine.util.agents.beans.config.LastNContextStrategyConfig")
public class LastNContextStrategyConfig extends ContextStrategyConfig {
    @UiField(label = "Keep Last Tokens", step = "model", section = "context", order = 20)
    @UiNumber
    private int keepLastTokens = 1024;

    public LastNContextStrategyConfig() {
        super(ContextStrategyType.LAST_N);
    }

    public int getKeepLastTokens() {
        return keepLastTokens;
    }

    public void setKeepLastTokens(final int keepLastTokens) {
        this.keepLastTokens = Math.max(1, keepLastTokens);
    }
}

package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiLookup;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("COMPACTION")
@BsonDiscriminator(value = "com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig")
public class CompactionContextStrategyConfig extends ContextStrategyConfig {
    @UiField(label = "Token Threshold", step = "model", section = "context", order = 30)
    @UiNumber
    private int tokenThreshold = 4096;

    @UiField(label = "Recency Threshold", step = "model", section = "context", order = 40)
    @UiNumber
    private int recencyThreshold = 1024;

    @UiField(label = "Compaction Model ID", step = "model", section = "context", order = 50)
    @UiLookup(assetType = AssetClass.MODEL)
    private String modelId;

    @UiField(label = "Prompt Template", step = "model", section = "context", order = 60)
    @UiText(multiline = true, rows = 4)
    private String promptTemplate;

    public CompactionContextStrategyConfig() {
        super(ContextStrategyType.COMPACTION);
    }

    public int getTokenThreshold() {
        return tokenThreshold;
    }

    public void setTokenThreshold(final int tokenThreshold) {
        this.tokenThreshold = tokenThreshold;
    }

    public int getRecencyThreshold() {
        return recencyThreshold;
    }

    public void setRecencyThreshold(final int recencyThreshold) {
        this.recencyThreshold = recencyThreshold;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(final String modelId) {
        this.modelId = modelId;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(final String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }
}

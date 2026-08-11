package com.agentengine.util.mongodb.infra;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.mongodb.infra.DefaultModelsConfig")
public class DefaultModelsConfig extends InfraConfig {
    public static final String TYPE = "DEFAULT_MODELS";
    public static final String CATEGORY = "DEFAULT_MODELS";
    public static final String CONFIG_ID = "default";

    private String titleModelId;
    private String compactionModelId;
    private String evaluatorModelId;
    private String embeddingModelId;
    private String chatModelId;

    public String getTitleModelId() {
        return titleModelId;
    }

    public void setTitleModelId(final String titleModelId) {
        this.titleModelId = titleModelId;
    }

    public String getCompactionModelId() {
        return compactionModelId;
    }

    public void setCompactionModelId(final String compactionModelId) {
        this.compactionModelId = compactionModelId;
    }

    public String getEvaluatorModelId() {
        return evaluatorModelId;
    }

    public void setEvaluatorModelId(final String evaluatorModelId) {
        this.evaluatorModelId = evaluatorModelId;
    }

    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(final String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public String getChatModelId() {
        return chatModelId;
    }

    public void setChatModelId(final String chatModelId) {
        this.chatModelId = chatModelId;
    }
}

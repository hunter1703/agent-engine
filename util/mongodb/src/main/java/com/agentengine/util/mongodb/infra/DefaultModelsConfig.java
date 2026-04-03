package com.agentengine.util.mongodb.infra;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "default_models")
public class DefaultModelsConfig extends InfraConfig {
    public static final String TYPE = "default_models";
    public static final String CATEGORY = "DEFAULT_MODELS";
    public static final String CONFIG_ID = "default";

    private String titleModelId;
    private String compactionModelId;
    private String evaluatorModelId;

    public DefaultModelsConfig() {
        super(TYPE);
    }

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
}

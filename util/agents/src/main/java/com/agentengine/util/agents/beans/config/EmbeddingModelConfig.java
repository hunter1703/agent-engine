package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("EMBEDDING")
@BsonDiscriminator("com.agentengine.util.agents.beans.config.EmbeddingModelConfig")
public class EmbeddingModelConfig extends ModelConfig {

    public EmbeddingModelConfig() {
        super(ModelType.EMBEDDING);
    }

    @UiField(label = "Vector Dimensions", step = "integration", order = 30)
    @UiNumber
    private int dimensions;

    @UiField(label = "Context Length", step = "integration", order = 40)
    @UiNumber
    private int contextLength;

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(final int dimensions) {
        this.dimensions = dimensions;
    }

    public int getContextLength() {
        return contextLength;
    }

    public void setContextLength(final int contextLength) {
        this.contextLength = contextLength;
    }
}

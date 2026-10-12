package com.agentengine.util.models.config;

import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.utils.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.genai.types.Modality;
import java.util.ArrayList;
import java.util.List;
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

  @UiField(label = "Max Batch Size", step = "integration", order = 50)
  @UiNumber
  private int maxBatchSize = 32;

  @UiField(label = "Modalities", step = "integration", order = 60)
  private List<Modality.Known> modalities = new ArrayList<>(List.of(Modality.Known.TEXT));

  public List<Modality.Known> getModalities() {
    return CollectionUtils.isEmpty(modalities) ? List.of(Modality.Known.TEXT) : modalities;
  }

  public void setModalities(final List<Modality.Known> modalities) {
    this.modalities = CollectionUtils.nullSafeList(modalities);
  }

  public boolean supports(final Modality.Known modality) {
    if (modality == null) {
      return false;
    }
    return getModalities().contains(modality);
  }

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

  public int getMaxBatchSize() {
    return maxBatchSize;
  }

  public void setMaxBatchSize(final int maxBatchSize) {
    this.maxBatchSize = maxBatchSize;
  }
}

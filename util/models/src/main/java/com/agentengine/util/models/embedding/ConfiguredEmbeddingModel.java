package com.agentengine.util.models.embedding;

import com.agentengine.util.models.ConfiguredModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

public final class ConfiguredEmbeddingModel extends ConfiguredModel<EmbeddingModel> {
  private final String modelId;
  private final int maxBatchSize;

  public ConfiguredEmbeddingModel(
      final String modelId, final EmbeddingModel model, final int maxBatchSize) {
    super(model);
    this.modelId = modelId;
    this.maxBatchSize = maxBatchSize;
  }

  /** The id of the model config this was built from. */
  public String modelId() {
    return modelId;
  }

  public int maxBatchSize() {
    return maxBatchSize;
  }
}

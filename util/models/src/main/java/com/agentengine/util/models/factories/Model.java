package com.agentengine.util.models.factories;

import com.google.adk.models.BaseLlm;

public abstract class Model<T> {

  private final T model;

  protected Model(final T model) {
    this.model = model;
  }

  public T model() {
    return model;
  }

  public static final class LLMModel extends Model<BaseLlm> {
    public LLMModel(final BaseLlm model) {
      super(model);
    }
  }

  public static final class EmbeddingModel
      extends Model<dev.langchain4j.model.embedding.EmbeddingModel> {
    private final int maxBatchSize;

    public EmbeddingModel(
        final dev.langchain4j.model.embedding.EmbeddingModel model, final int maxBatchSize) {
      super(model);
      this.maxBatchSize = maxBatchSize;
    }

    public int maxBatchSize() {
      return maxBatchSize;
    }
  }
}

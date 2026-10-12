package com.agentengine.util.models.factories;

import com.agentengine.util.models.config.EmbeddingModelConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;

public interface EmbeddingModelFactory {

  EmbeddingModel build(EmbeddingModelConfig config);

  String type();
}

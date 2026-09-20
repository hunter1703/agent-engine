package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.models.factories.EmbeddingModelFactory;
import com.agentengine.util.vectordb.VectorDbUtils;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.List;
import java.util.Map;

/**
 * Terminal pipeline stage that populates the {@link KnowledgeChunk#getVector()} field for each
 * chunk by calling the configured {@link EmbeddingModel}.
 *
 * <p>This stage must be the last in every pipeline so that all prior splitting and merging is
 * complete before embeddings are computed (embeddings are expensive and should not be wasted on
 * intermediate chunks).
 */
public final class EmbeddingStage extends ChunkingStage {

  private final String embeddingModelId;
  private final EmbeddingModelFactory embeddingModelFactory;
  private final LazyLoader<Map<String, String>> fieldVsVectorName;

  public EmbeddingStage(
      final String embeddingModelId, final EmbeddingModelFactory embeddingModelFactory) {
    this.embeddingModelId = embeddingModelId;
    this.embeddingModelFactory = embeddingModelFactory;
    this.fieldVsVectorName = new LazyLoader<>(() -> VectorDbUtils.vectorNames(KnowledgeChunk.class));
  }

  @Override
  public List<KnowledgeChunk> apply(final List<KnowledgeChunk> chunks) {
    final EmbeddingModel model = embeddingModelFactory.get(embeddingModelId);
    try {
      final String textVector = fieldVsVectorName.get().get(KnowledgeChunk.FIELD_TEXT);
      for (final KnowledgeChunk chunk : chunks) {
        final String text = chunk.getText();
        if (text != null && !text.isBlank()) {
          final float[] vector = model.embed(text).content().vector();
          chunk.setVector(textVector, vector);
        }
      }
      return chunks;
    } finally {
      embeddingModelFactory.release(embeddingModelId);
    }
  }
}

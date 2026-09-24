package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.vectordb.VectorDbUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Terminal pipeline stage that populates the {@link KnowledgeChunk#getVector()} field for each
 * chunk by calling the configured {@link Model.EmbeddingModel}.
 *
 * <p>This stage must be the last in every pipeline so that all prior splitting and merging is
 * complete before embeddings are computed (embeddings are expensive and should not be wasted on
 * intermediate chunks).
 */
public final class EmbeddingStage extends ChunkingStage {

  private final String embeddingModelId;
  private final ModelProvider modelProvider;
  private final LazyLoader<Map<String, String>> fieldVsVectorName;

  public EmbeddingStage(final String embeddingModelId, final ModelProvider modelProvider) {
    this.embeddingModelId = embeddingModelId;
    this.modelProvider = modelProvider;
    this.fieldVsVectorName =
        new LazyLoader<>(() -> VectorDbUtils.vectorNames(KnowledgeChunk.class));
  }

  @Override
  public List<KnowledgeChunk> apply(final List<KnowledgeChunk> chunks) {
    if (CollectionUtils.isEmpty(chunks)) {
      return chunks;
    }
    try (RefCounted<Model.EmbeddingModel> refCounted =
        modelProvider.getEmbeddingModel(embeddingModelId)) {
      final String textVector = fieldVsVectorName.get().get(KnowledgeChunk.FIELD_TEXT);
      final Model.EmbeddingModel model = refCounted.value();
      for (final List<KnowledgeChunk> batch :
          CollectionUtils.batches(chunks, model.maxBatchSize())) {
        final List<TextSegment> segments =
            batch.stream().map(chunk -> TextSegment.from(chunk.getText())).toList();
        final Response<List<Embedding>> response = model.model().embedAll(segments);
        final List<Embedding> embeddings = response.content();

        final Iterator<KnowledgeChunk> chunkIterator = batch.iterator();
        final Iterator<Embedding> embeddingIterator = embeddings.iterator();
        while (chunkIterator.hasNext() && embeddingIterator.hasNext()) {
          chunkIterator.next().setVector(textVector, embeddingIterator.next().vector());
        }
      }
      return chunks;
    }
  }
}

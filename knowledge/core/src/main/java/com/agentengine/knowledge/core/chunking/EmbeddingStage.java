package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.models.factories.EmbeddingUtils;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.vectordb.VectorDbUtils;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;

/**
 * Terminal pipeline stage that populates the {@link KnowledgeChunk#getVector()} field for each
 * chunk by calling the configured {@link Model.EmbeddingModel}.
 *
 * <p>This stage must be the last in every pipeline so that all prior splitting and merging is
 * complete before embeddings are computed (embeddings are expensive and should not be wasted on
 * intermediate chunks).
 *
 * <p>Batches are embedded one at a time, in order — not concurrently. A batch's own size is already
 * this stage's one throughput knob ({@link Model.EmbeddingModel#maxBatchSize()}); running several
 * {@code embedAll} calls concurrently on top of that would be a second, overlapping lever for the
 * same goal, with no evidence it helps a model that already appears to process a batch's items
 * close to serially. Each embedded batch is still emitted downstream as soon as it's ready, so a
 * caller persisting these chunks isn't forced to wait for the whole document to finish embedding
 * before writing anything.
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
  public Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return Flowable.using(
        () -> modelProvider.getEmbeddingModel(embeddingModelId),
        refCounted ->
            chunks
                .buffer(refCounted.value().maxBatchSize())
                .concatMap(batch -> embedBatch(refCounted.value(), batch)),
        RefCounted::close);
  }

  private Flowable<KnowledgeChunk> embedBatch(
      final Model.EmbeddingModel model, final List<KnowledgeChunk> batch) {
    if (batch.isEmpty()) {
      return Flowable.empty();
    }
    return Flowable.fromCallable(
            () -> {
              final String textVector = fieldVsVectorName.get().get(KnowledgeChunk.FIELD_TEXT);
              final List<float[]> vectors =
                  EmbeddingUtils.embedAll(
                      model, batch.stream().map(KnowledgeChunk::getText).toList());
              for (int i = 0; i < batch.size(); i++) {
                batch.get(i).setVector(textVector, vectors.get(i));
              }
              return batch;
            })
        .flatMap(Flowable::fromIterable);
  }
}

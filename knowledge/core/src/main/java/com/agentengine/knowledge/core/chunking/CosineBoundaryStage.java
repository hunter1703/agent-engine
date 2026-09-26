package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.Batcher;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.models.factories.Model.EmbeddingModel;
import com.agentengine.util.models.factories.ModelProvider;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cuts chunk boundaries where the cosine similarity between adjacent sentence embeddings drops
 * below a threshold, or the running batch would grow past {@code maxSegmentSize}. Merges sentences
 * into coherent chunks — the chunk whose embedding was dissimilar from (or that pushed the batch
 * past) the threshold ends that batch rather than starting the next one.
 *
 * <p>An image chunk (see {@link ChunkUtils#isMedia}) is never embedded or folded into a merged
 * batch — it forces whatever batch is pending to flush, then passes through unchanged, so it always
 * ends up its own chunk with the surrounding text merged up to and after it.
 */
public final class CosineBoundaryStage extends ChunkingStage {

  private final int maxSegmentSize;
  private final double similarityThreshold;
  private final String embeddingModelId;
  private final ModelProvider modelProvider;

  public CosineBoundaryStage(
      final int maxSegmentSize,
      final double similarityThreshold,
      final String embeddingModelId,
      final ModelProvider modelProvider) {
    this.maxSegmentSize = maxSegmentSize;
    this.similarityThreshold = similarityThreshold;
    this.embeddingModelId = embeddingModelId;
    this.modelProvider = modelProvider;
  }

  @Override
  public Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return Flowable.using(
        () -> modelProvider.getEmbeddingModel(embeddingModelId),
        refCounted -> {
          final List<KnowledgeChunk> results = new ArrayList<>();
          final SimilarityBatcher batcher =
              new SimilarityBatcher(
                  refCounted.value(), batch -> results.add(ChunkUtils.mergeTexts(batch)));
          return chunks
              .concatMap(
                  chunk -> {
                    results.clear();
                    if (ChunkUtils.isMedia(chunk)) {
                      batcher.flush();
                      results.add(chunk);
                    } else {
                      batcher.add(chunk);
                    }
                    return Flowable.fromIterable(List.copyOf(results));
                  })
              .concatWith(
                  Flowable.defer(
                      () -> {
                        results.clear();
                        batcher.flush();
                        return Flowable.fromIterable(List.copyOf(results));
                      }));
        },
        RefCounted::close);
  }

  /**
   * Flushes once the chunk just added is dissimilar from the one before it, or its addition pushed
   * the buffer past {@code maxSegmentSize} — either way, that chunk is included in what flushes
   * (it's already in the buffer), and the next chunk starts a fresh, empty buffer. The first chunk
   * ever added has no predecessor to compare against, so it never triggers a flush by itself.
   */
  private final class SimilarityBatcher extends Batcher<KnowledgeChunk> {
    private final EmbeddingModel model;
    private float[] lastVector;

    private SimilarityBatcher(
        final EmbeddingModel model, final Consumer<List<KnowledgeChunk>> onFlush) {
      super(onFlush);
      this.model = model;
    }

    @Override
    protected boolean shouldFlush() {
      final KnowledgeChunk latest = buffer.getLast();
      final float[] vector = model.model().embed(latest.getText()).content().vector();
      final boolean dissimilar =
          lastVector != null && cosine(lastVector, vector) < similarityThreshold;
      final boolean tooLarge =
          buffer.stream().mapToInt(ChunkUtils::textLength).sum() > maxSegmentSize;
      lastVector = vector;
      return dissimilar || tooLarge;
    }
  }

  private static double cosine(final float[] a, final float[] b) {
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    final double denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom == 0 ? 0 : dot / denom;
  }
}

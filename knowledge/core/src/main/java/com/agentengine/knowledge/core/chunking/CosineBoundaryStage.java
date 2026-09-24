package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.models.factories.Model.EmbeddingModel;
import com.agentengine.util.models.factories.ModelProvider;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Cuts chunk boundaries where the cosine similarity between adjacent sentence embeddings drops
 * below a threshold. Merges sentences into coherent chunks.
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

  /**
   * A boundary only ever compares one chunk's embedding against the next one's, and a merged batch
   * only ever accumulates chunks in order — so unlike {@link LlmBoundaryStage} (which re-examines
   * an arbitrary earlier window once a boundary lands), this never needs more than the single chunk
   * still awaiting its cut decision plus the batch accumulated so far. Embeds and decides
   * boundaries as chunks arrive rather than only once the whole input is known.
   *
   * <p>The cut decision for a chunk depends on its similarity to the <em>next</em> chunk (matching
   * the original batch semantics: chunk {@code i} starts a new batch when it's dissimilar from
   * chunk {@code i+1}), so a chunk can't be resolved the moment it arrives — it's held until the
   * following chunk's embedding is available to compare against, then resolved. The last chunk has
   * no successor to compare against, so it never triggers a similarity cut, exactly as before.
   */
  @Override
  public Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return Flowable.using(
        () -> modelProvider.getEmbeddingModel(embeddingModelId),
        refCounted -> {
          final Accumulator accumulator = new Accumulator(refCounted.value());
          return chunks
              .concatMap(
                  chunk -> accumulator.accept(chunk).map(Flowable::just).orElseGet(Flowable::empty))
              .concatWith(Flowable.defer(() -> Flowable.fromIterable(accumulator.flush())));
        },
        RefCounted::close);
  }

  /**
   * Sequential, mutable per-subscription state. Safe only because {@code concatMap} guarantees
   * {@link #accept} is never called concurrently with itself or with {@link #flush}.
   */
  private final class Accumulator {
    private final EmbeddingModel model;
    private KnowledgeChunk pendingChunk;
    private float[] pendingVector;
    private final List<KnowledgeChunk> batch = new ArrayList<>();
    private int batchChars;

    Accumulator(final EmbeddingModel model) {
      this.model = model;
    }

    Optional<KnowledgeChunk> accept(final KnowledgeChunk chunk) {
      final float[] vector = model.model().embed(chunk.getText()).content().vector();
      if (pendingChunk == null) {
        pendingChunk = chunk;
        pendingVector = vector;
        return Optional.empty();
      }
      final boolean cut = cosine(pendingVector, vector) < similarityThreshold;
      final Optional<KnowledgeChunk> emitted = resolvePending(cut);
      pendingChunk = chunk;
      pendingVector = vector;
      return emitted;
    }

    List<KnowledgeChunk> flush() {
      if (pendingChunk == null) {
        return List.of();
      }
      // The last chunk has no successor, so it never triggers a similarity cut — only size can.
      final Optional<KnowledgeChunk> resolved = resolvePending(false);
      pendingChunk = null;
      final Optional<KnowledgeChunk> merged =
          batch.isEmpty() ? Optional.empty() : Optional.of(ChunkUtils.mergeTexts(batch));
      return Stream.of(resolved, merged).flatMap(Optional::stream).toList();
    }

    /** Folds {@link #pendingChunk} into the running batch, flushing it first if this cuts. */
    private Optional<KnowledgeChunk> resolvePending(final boolean cut) {
      final boolean tooLarge =
          batchChars + pendingChunk.getText().length() > maxSegmentSize && !batch.isEmpty();
      final Optional<KnowledgeChunk> emitted;
      if ((cut || tooLarge) && !batch.isEmpty()) {
        emitted = Optional.of(ChunkUtils.mergeTexts(batch));
        batch.clear();
        batchChars = 0;
      } else {
        emitted = Optional.empty();
      }
      batch.add(pendingChunk);
      batchChars += pendingChunk.getText().length();
      return emitted;
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

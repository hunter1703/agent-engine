package com.agentengine.util.models.factories;

import com.agentengine.util.common.utils.CollectionUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;

public final class EmbeddingUtils {

  private EmbeddingUtils() {}

  /**
   * The embedding of each of {@code texts}, in order, embedded with {@code model} in batches of its
   * {@link Model.EmbeddingModel#maxBatchSize()}, one at a time.
   */
  public static List<float[]> embedAll(final Model.EmbeddingModel model, final List<String> texts) {
    final List<float[]> vectors = new ArrayList<>(texts.size());
    for (final List<String> batch : CollectionUtils.batches(texts, model.maxBatchSize())) {
      final List<TextSegment> segments = batch.stream().map(TextSegment::from).toList();
      for (final Embedding embedding : model.model().embedAll(segments).content()) {
        vectors.add(embedding.vector());
      }
    }
    return vectors;
  }
}

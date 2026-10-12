package com.agentengine.util.models.factories;

import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.models.embedding.ConfiguredEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;

import java.util.ArrayList;
import java.util.List;

public final class EmbeddingUtils {

  private EmbeddingUtils() {}

  /**
   * The embedding of each of {@code texts}, in order, embedded with {@code model} in batches of its
   * {@link ConfiguredEmbeddingModel#maxBatchSize()}, one at a time.
   */
  public static List<float[]> embedAll(
      final ConfiguredEmbeddingModel model, final List<String> texts) {
    final List<float[]> vectors = new ArrayList<>(texts.size());
    for (final List<String> batch : CollectionUtils.batches(texts, model.maxBatchSize())) {
      final List<TextSegment> segments = batch.stream().map(TextSegment::from).toList();
      for (final Embedding embedding : model.model().embedAll(segments).content()) {
        vectors.add(embedding.vector());
      }
    }
    return vectors;
  }

  /**
   * For each of {@code images}, in order, the vectors {@code model} finds in it, which may be
   * several or none. The images are embedded one at a time, since a response does not say which
   * image a vector was found in.
   */
  public static List<List<float[]>> embedImages(
      final ConfiguredEmbeddingModel model, final List<ImageContent> images) {
    final List<List<float[]>> vectors = new ArrayList<>(images.size());
    for (final ImageContent image : images) {
      final EmbeddingRequest request = EmbeddingRequest.builder().input(image).build();
      vectors.add(
          model.model().embed(request).embeddings().stream().map(Embedding::vector).toList());
    }
    return vectors;
  }
}

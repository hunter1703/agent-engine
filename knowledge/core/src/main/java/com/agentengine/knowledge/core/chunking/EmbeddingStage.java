package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.utils.ExceptionUtils;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.embedding.ConfiguredEmbeddingModel;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.vectordb.VectorDbUtils;
import com.google.genai.types.Modality;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import io.reactivex.rxjava3.core.Flowable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns each chunk into the chunks that carry its embeddings, in its place. Each embedding model
 * of the text and description makes one chunk, and each vector an image embedding model finds in
 * the chunk's image, such as one per face, makes one; each names its model as the {@code
 * embedder}. A chunk nothing is embedded from is passed on as it is.
 *
 * <p>The {@code n}th chunk of the stream, and every chunk made from it, has the chunk index
 * {@code n}.
 */
public final class EmbeddingStage extends IndexingStage {

  private final ModelProvider modelProvider;
  private final FileService fileService;
  private final Map<String, List<String>> modalityVsEmbeddingModelIds;
  private final Map<String, String> fieldVsVectorName =
      VectorDbUtils.vectorNames(KnowledgeChunk.class);

  public EmbeddingStage(
      final ModelProvider modelProvider,
      final FileService fileService,
      final Map<String, List<String>> modalityVsEmbeddingModelIds) {
    this.modelProvider = modelProvider;
    this.fileService = fileService;
    this.modalityVsEmbeddingModelIds = modalityVsEmbeddingModelIds;
  }

  @Override
  public Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return Flowable.defer(
        () -> {
          final AtomicInteger nextIndex = new AtomicInteger();
          return chunks.concatMapIterable(chunk -> {
            final int chunkIndex = nextIndex.getAndIncrement();
            final List<KnowledgeChunk> embedded = new ArrayList<>();
            if (hasText(chunk)) {
              for (final String modelId : embeddingModelIds(Modality.Known.TEXT)) {
                embedded.add(embedText(modelId, chunk));
              }
            }
            if (FileUtils.isImageFile(chunk.getMedia())) {
              for (final String modelId : embeddingModelIds(Modality.Known.IMAGE)) {
                embedded.addAll(embedImage(modelId, chunk));
              }
            }
            if (embedded.isEmpty()) {
              embedded.add(chunk);
            }
            embedded.forEach(embeddedChunk -> {
              embeddedChunk.setChunkIndex(chunkIndex);
              embeddedChunk.setChunkStart(chunk.getChunkStart());
              embeddedChunk.setChunkEnd(chunk.getChunkEnd());
            });
            return embedded;
          });
        });
  }

  private List<String> embeddingModelIds(final Modality.Known modality) {
    return modalityVsEmbeddingModelIds.getOrDefault(modality.name(), List.of());
  }

  private KnowledgeChunk embedText(final String modelId, final KnowledgeChunk chunk) {
    try (RefCounted<ConfiguredEmbeddingModel> model = modelProvider.getEmbeddingModel(modelId)) {
      final KnowledgeChunk embedded = chunk.copy().withEmbedder(model.value().modelId());
      if (StringUtils.isNotBlank(chunk.getText())) {
        embedded.setVector(
            fieldVsVectorName.get(KnowledgeChunk.FIELD_TEXT),
            model.value().model().embed(chunk.getText()).content().vector());
      }
      if (StringUtils.isNotBlank(chunk.getDescription())) {
        embedded.setVector(
            fieldVsVectorName.get(KnowledgeChunk.FIELD_DESCRIPTION),
            model.value().model().embed(chunk.getDescription()).content().vector());
      }
      return embedded;
    }
  }

  private List<KnowledgeChunk> embedImage(final String modelId, final KnowledgeChunk chunk) {
    try (RefCounted<ConfiguredEmbeddingModel> model = modelProvider.getEmbeddingModel(modelId)) {
      final EmbeddingResponse response = model.value().model().embed(EmbeddingRequest.builder().input(readImage(chunk.getMedia())).build());
      final List<KnowledgeChunk> embedded = new ArrayList<>();
      final String vectorField = fieldVsVectorName.get(KnowledgeChunk.FIELD_MEDIA);
      for (final Embedding embedding : response.embeddings()) {
        final KnowledgeChunk embeddedChunk = chunk.copy().withEmbedder(model.value().modelId());
        embeddedChunk.setVector(vectorField, embedding.vector());
        embedded.add(embeddedChunk);
      }
      return embedded;
    }
  }

  private ImageContent readImage(final FileDetails media) {
    try (final InputStream content = fileService.getContent(media)) {
      return new ImageContent(
          Base64.getEncoder().encodeToString(content.readAllBytes()), media.mimeType());
    } catch (final IOException e) {
      throw ExceptionUtils.wrapInRuntimeException(e);
    }
  }

  private static boolean hasText(final KnowledgeChunk chunk) {
    return StringUtils.isNotBlank(chunk.getText())
        || StringUtils.isNotBlank(chunk.getDescription());
  }
}

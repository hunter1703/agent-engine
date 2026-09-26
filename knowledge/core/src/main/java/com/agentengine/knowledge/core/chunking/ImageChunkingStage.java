package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.core.pipeline.MediaService;
import com.agentengine.knowledge.core.pipeline.MediaService.ImageDescription;
import com.agentengine.util.common.StringUtils;
import io.reactivex.rxjava3.core.Flowable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Turns each still-undescribed image chunk (see {@link ChunkUtils#isMedia}) into one or two real
 * text chunks by describing it with the configured vision model: a {@code <image>} chunk holding
 * the description, and — only when the image has visible text — a sibling {@code <image-text>}
 * chunk holding its verbatim OCR transcription. Kept separate rather than concatenated into one
 * chunk so each can be embedded and retrieved on its own terms; a shared {@code id} attribute ties
 * the two together for a reader that retrieves both. Every other chunk passes through unchanged.
 *
 * <p>Shared by any knowledge whose content is, or contains, an image: standalone image knowledge
 * runs this as its pipeline's only stage before embedding, reading the image's raw bytes directly
 * (see {@link #apply(InputStream)}); a PDF's pipeline appends this after {@code PdfSplitterStage}
 * and whatever splitting or merging the configured chunking strategy runs in between, so every
 * image chunk those stages left untouched is described exactly once, right before embedding.
 */
public final class ImageChunkingStage extends ChunkingStage implements StreamingChunkingStage {

  private final Knowledge knowledge;
  private final MediaService mediaService;

  public ImageChunkingStage(final Knowledge knowledge, final MediaService mediaService) {
    this.knowledge = knowledge;
    this.mediaService = mediaService;
  }

  @Override
  public Flowable<KnowledgeChunk> apply(final InputStream content) {
    final byte[] imageBytes;
    try {
      imageBytes = content.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    final KnowledgeChunk placeholder = new KnowledgeChunk();
    placeholder.setBytes(imageBytes);
    placeholder.setMimeType(knowledge.getFileDetails().mimeType());
    return describe(placeholder);
  }

  @Override
  protected Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return chunks.concatMap(
        chunk ->
            ChunkUtils.isImage(chunk) && chunk.getBytes() != null
                ? describe(chunk)
                : Flowable.just(chunk));
  }

  private Flowable<KnowledgeChunk> describe(final KnowledgeChunk chunk) {
    final ImageDescription described = mediaService.describeImage(knowledge, chunk.getBytes());
    final String imageId = UUID.randomUUID().toString();
    final KnowledgeChunk newChunk = new KnowledgeChunk();
    newChunk.setChunkStart(chunk.getChunkStart());
    newChunk.setChunkEnd(chunk.getChunkEnd());
    newChunk.setMimeType(chunk.getMimeType());
    newChunk.setText(tag("image", imageId, described.description()));
    if (StringUtils.isBlank(described.ocrText())) {
      return Flowable.just(newChunk);
    }

    final KnowledgeChunk textChunk = new KnowledgeChunk();
    textChunk.setChunkStart(chunk.getChunkStart());
    textChunk.setChunkEnd(chunk.getChunkEnd());
    textChunk.setMimeType(chunk.getMimeType());
    textChunk.setText(tag("image-text", imageId, described.ocrText()));
    return Flowable.just(newChunk, textChunk);
  }

  private static String tag(final String name, final String imageId, final String text) {
    return "<" + name + " id=\"" + imageId + "\">" + text + "</" + name + ">";
  }
}

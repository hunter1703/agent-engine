package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.beans.MediaDescription;
import com.agentengine.knowledge.api.services.MediaService;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.cloudstorage.FileService;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Describes images with the vision model, filling the {@code description} (what the image shows)
 * and the {@code text} (the text visible in it) of their chunks.
 *
 * <p>As a pipeline's first stage it emits the one chunk of the knowledge's own image file. Later in
 * a pipeline, as for a PDF, it describes the image chunks that arrive. An image is left undescribed
 * when the knowledge's settings turn {@code describeMedia} off.
 */
public final class ImageChunkingStage extends RootIndexingStage {

  private final MediaService mediaService;

  public ImageChunkingStage(
      final FileService fileService, final MediaService mediaService, final Knowledge knowledge) {
    super(fileService, knowledge);
    this.mediaService = mediaService;
  }

  @Override
  protected Flowable<KnowledgeChunk> apply() {
    final KnowledgeChunk chunk = new KnowledgeChunk();
    chunk.setChunkStart(0);
    chunk.setChunkEnd(1);
    chunk.setMedia(knowledge().getFileDetails());
    return Flowable.just(describe(chunk));
  }

  @Override
  protected Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return chunks.map(chunk -> ChunkUtils.isImage(chunk) ? describe(chunk) : chunk);
  }

  private KnowledgeChunk describe(final KnowledgeChunk chunk) {
    final KnowledgeSettings settings = knowledge().getSettings();
    if (settings != null && !settings.isDescribeMedia()) {
      return chunk;
    }
    final MediaDescription description = mediaService.describe(chunk.getMedia());
    chunk.setText(description.transcript());
    chunk.setDescription(description.description());
    return chunk;
  }
}

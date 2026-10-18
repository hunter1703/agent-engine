package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.MediaService;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.chunking.ImageChunkingStage;
import com.agentengine.knowledge.core.chunking.IndexingStage;
import com.agentengine.knowledge.core.chunking.RootIndexingStage;
import com.agentengine.knowledge.core.repository.KnowledgeRepository;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * Indexes image knowledge by describing it with the configured vision model — {@link
 * ImageChunkingStage} alone, skipping the knowledge's configured chunking strategy entirely (an
 * image's description is one atomic unit, not something to split further).
 */
@Singleton
public class ImageKnowledgeIndexer extends AbstractKnowledgeIndexer {

  private static final int PRIORITY = 100;

  private final MediaService mediaService;

  @Inject
  public ImageKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeRepository knowledgeRepository,
      final ModelProvider modelProvider,
      final MediaService mediaService,
      final FileService fileService) {
    super(chunkingPipelineFactory, knowledgeRepository, modelProvider, fileService);
    this.mediaService = mediaService;
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return FileUtils.isImageFile(knowledge.getFileDetails());
  }

  @Override
  protected List<IndexingStage> stages(final Knowledge knowledge) {
    return List.of();
  }

  @Override
  protected RootIndexingStage rootStage(Knowledge knowledge) {
    return new ImageChunkingStage(fileService, mediaService, knowledge);
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}

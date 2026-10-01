package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.chunking.ChunkingStage;
import com.agentengine.knowledge.core.chunking.ImageChunkingStage;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
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
public class ImageKnowledgeIndexer extends AbstractTextKnowledgeIndexer {

  private static final int PRIORITY = 100;

  private final MediaService mediaService;

  @Inject
  public ImageKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeChunkStore vectorStore,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider,
      final MediaService mediaService) {
    super(chunkingPipelineFactory, vectorStore, defaultModelsRepository, modelProvider);
    this.mediaService = mediaService;
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return FileUtils.isImageFile(knowledge.getFileDetails());
  }

  @Override
  protected List<ChunkingStage> stages(final Knowledge knowledge) {
    return List.of(new ImageChunkingStage(knowledge, mediaService));
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}

package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.MediaService;
import com.agentengine.knowledge.core.chunking.*;
import com.agentengine.knowledge.core.repository.KnowledgeRepository;
import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Indexes PDF knowledge by walking it in true document order — text and embedded images interleaved
 * exactly as they're drawn.
 */
@Singleton
public class PdfKnowledgeIndexer extends AbstractKnowledgeIndexer {

  private static final int PRIORITY = 100;

  private final MediaService mediaService;
  private final CloudStorageService cloudStorageService;

  @Inject
  public PdfKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeRepository knowledgeRepository,
      final ModelProvider modelProvider,
      final MediaService mediaService,
      final FileService fileService,
      final CloudStorageService cloudStorageService) {
    super(chunkingPipelineFactory, knowledgeRepository, modelProvider, fileService);
    this.mediaService = mediaService;
    this.cloudStorageService = cloudStorageService;
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return FileUtils.isPdfFile(knowledge.getFileDetails());
  }

  @Override
  protected List<IndexingStage> stages(final Knowledge knowledge) {
    final List<IndexingStage> stages =
        new ArrayList<>(chunkingPipelineFactory.getConfiguredStages(knowledge));
    stages.add(new ImageChunkingStage(fileService, mediaService, knowledge));
    return stages;
  }

  @Override
  protected RootIndexingStage rootStage(Knowledge knowledge) {
    return new PdfChunkingStage(fileService, knowledge, cloudStorageService);
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}

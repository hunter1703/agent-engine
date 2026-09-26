package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.chunking.ChunkingStage;
import com.agentengine.knowledge.core.chunking.ImageChunkingStage;
import com.agentengine.knowledge.core.chunking.PdfSplitterStage;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.FileUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Indexes PDF knowledge by walking it in true document order — text and embedded images interleaved
 * exactly as they're drawn, not extracted as two separate passes and concatenated: {@link
 * PdfSplitterStage} does that walk as the pipeline's first stage, the knowledge's configured
 * chunking strategy runs on the resulting text chunks, and {@link ImageChunkingStage} describes
 * whatever image chunks are left over, right before embedding.
 */
@Singleton
public class PdfKnowledgeIndexer extends AbstractTextKnowledgeIndexer {

  private static final int PRIORITY = 100;

  private final MediaService mediaService;

  @Inject
  public PdfKnowledgeIndexer(
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
    return FileUtils.isPdfFile(knowledge.getFileDetails());
  }

  @Override
  protected List<ChunkingStage> stages(final Knowledge knowledge) {
    final List<ChunkingStage> stages = new ArrayList<>();
    stages.add(new PdfSplitterStage());
    stages.addAll(super.stages(knowledge));
    stages.add(new ImageChunkingStage(knowledge, mediaService));
    return stages;
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}

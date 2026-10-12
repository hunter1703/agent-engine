package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.chunking.IndexingStage;
import com.agentengine.knowledge.core.chunking.RootIndexingStage;
import com.agentengine.knowledge.core.chunking.SingleChunkStage;
import com.agentengine.knowledge.core.repository.KnowledgeRepository;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/** Indexes any knowledge not claimed by a more specific indexer, as plain text. */
@Singleton
public class TextKnowledgeIndexer extends AbstractKnowledgeIndexer {

  @Inject
  public TextKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeRepository knowledgeRepository,
      final ModelProvider modelProvider,
      final FileService fileService) {
    super(
        chunkingPipelineFactory,
        knowledgeRepository,
        modelProvider,
        fileService);
  }

  @Override
  protected List<IndexingStage> stages(Knowledge knowledge) {
    return chunkingPipelineFactory.getConfiguredStages(knowledge);
  }

  @Override
  protected RootIndexingStage rootStage(Knowledge knowledge) {
    return new SingleChunkStage(fileService, knowledge);
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return FileUtils.isTextFile(knowledge.getFileDetails());
  }

  @Override
  public int priority() {
    return Integer.MAX_VALUE;
  }
}

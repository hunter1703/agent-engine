package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.chunking.IndexingStage;
import com.agentengine.knowledge.core.chunking.MarkdownChunkingStage;
import com.agentengine.knowledge.core.chunking.RootIndexingStage;
import com.agentengine.knowledge.core.repository.KnowledgeRepository;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class MarkdownKnowledgeIndexer extends AbstractKnowledgeIndexer {

  private static final int PRIORITY = 100;

  @Inject
  public MarkdownKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeRepository knowledgeRepository,
      final ModelProvider modelProvider,
      final FileService fileService) {
    super(chunkingPipelineFactory, knowledgeRepository, modelProvider, fileService);
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return FileUtils.isMarkdownFile(knowledge.getFileDetails());
  }

  @Override
  protected List<IndexingStage> stages(final Knowledge knowledge) {
    return chunkingPipelineFactory.getConfiguredStages(knowledge);
  }

  @Override
  protected RootIndexingStage rootStage(Knowledge knowledge) {
    return new MarkdownChunkingStage(fileService, knowledge);
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}

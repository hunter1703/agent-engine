package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.chunking.ChunkingStage;
import com.agentengine.knowledge.core.chunking.MarkdownSplitterStage;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.FileUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MarkdownKnowledgeIndexer extends AbstractTextKnowledgeIndexer {

  private static final int PRIORITY = 100;

  @Inject
  public MarkdownKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeChunkStore vectorStore,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider) {
    super(chunkingPipelineFactory, vectorStore, defaultModelsRepository, modelProvider);
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return FileUtils.isMarkdownFile(knowledge.getFileDetails());
  }

  @Override
  protected List<ChunkingStage> stages(final Knowledge knowledge) {
    final List<ChunkingStage> stages = new ArrayList<>();
    stages.add(new MarkdownSplitterStage());
    stages.addAll(super.stages(knowledge));
    return stages;
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}

package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Indexes any knowledge not claimed by a more specific indexer, as plain text. */
@Singleton
public class TextKnowledgeIndexer extends AbstractTextKnowledgeIndexer {

  @Inject
  public TextKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeChunkStore vectorStore,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider) {
    super(chunkingPipelineFactory, vectorStore, defaultModelsRepository, modelProvider);
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return true;
  }

  @Override
  public int priority() {
    return Integer.MAX_VALUE;
  }
}

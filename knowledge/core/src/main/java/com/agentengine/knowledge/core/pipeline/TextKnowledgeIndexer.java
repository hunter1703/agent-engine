package com.agentengine.knowledge.core.pipeline;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/** Indexes any knowledge not claimed by a more specific indexer, as plain text. */
@Singleton
public class TextKnowledgeIndexer extends AbstractTextKnowledgeIndexer {

  @Inject
  public TextKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeChunkStore vectorStore,
      final FileService fileService,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider) {
    super(
        chunkingPipelineFactory, vectorStore, fileService, defaultModelsRepository, modelProvider);
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return true;
  }

  @Override
  protected Reader read(final Knowledge knowledge, final InputStream content) {
    return new InputStreamReader(content, UTF_8);
  }

  @Override
  public int priority() {
    return Integer.MAX_VALUE;
  }
}

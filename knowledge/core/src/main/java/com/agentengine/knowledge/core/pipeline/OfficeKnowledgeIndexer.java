package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.FileUtils;
import com.agentengine.util.models.factories.ModelProvider;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

/**
 * Indexes Word, PowerPoint, and Excel knowledge (old or OOXML format) by extracting its text with
 * Apache POI before chunking.
 */
@Singleton
public class OfficeKnowledgeIndexer extends AbstractTextKnowledgeIndexer {

  private static final int PRIORITY = 100;

  @Inject
  public OfficeKnowledgeIndexer(
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
    return FileUtils.isOfficeFile(knowledge.getFileDetails());
  }

  @Override
  protected Reader read(final Knowledge knowledge, final InputStream content) {
    return new StringReader(new ApachePoiDocumentParser().parse(content).text());
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}

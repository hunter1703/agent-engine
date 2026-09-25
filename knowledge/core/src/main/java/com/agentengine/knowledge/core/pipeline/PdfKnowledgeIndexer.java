package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.FileUtils;
import com.agentengine.util.models.factories.ModelProvider;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

/**
 * Indexes PDF knowledge by extracting its text with Apache PDFBox before chunking. PDFBox reads the
 * PDF's own internal structure directly rather than a visual/OCR pass, so it extracts a scanned or
 * image-only PDF's text as empty — those need a different extraction mechanism, not yet supported
 * here.
 */
@Singleton
public class PdfKnowledgeIndexer extends AbstractTextKnowledgeIndexer {

  private static final int PRIORITY = 100;

  @Inject
  public PdfKnowledgeIndexer(
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
    return FileUtils.isPdfFile(knowledge.getFileDetails());
  }

  @Override
  protected Reader read(final InputStream content) {
    return new StringReader(new ApachePdfBoxDocumentParser().parse(content).text());
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}

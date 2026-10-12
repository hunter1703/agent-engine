package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.cloudstorage.FileService;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import io.reactivex.rxjava3.core.Flowable;
import java.io.InputStream;

/**
 * The first stage of an Office document's (Word, PowerPoint, or Excel; old or OOXML format)
 * chunking pipeline: extracts its text with Apache POI and emits it as a single seed chunk for
 * whatever splitting strategy is configured to run next.
 */
public final class OfficeChunkingStage extends RootIndexingStage {

  public OfficeChunkingStage(FileService fileService, Knowledge knowledge) {
    super(fileService, knowledge);
  }

  @Override
  protected Flowable<KnowledgeChunk> apply() {
    return Flowable.using(
        this::readFile,
        content -> {
          final String text = new ApachePoiDocumentParser().parse(content).text();
          final KnowledgeChunk chunk = new KnowledgeChunk();
          chunk.setText(text);
          chunk.setChunkStart(0);
          chunk.setChunkEnd(text.length());
          return Flowable.just(chunk);
        },
        InputStream::close);
  }
}

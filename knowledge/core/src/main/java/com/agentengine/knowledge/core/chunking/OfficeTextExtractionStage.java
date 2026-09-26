package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import io.reactivex.rxjava3.core.Flowable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The first stage of an Office document's (Word, PowerPoint, or Excel; old or OOXML format)
 * chunking pipeline: extracts its text with Apache POI and emits it as a single seed chunk for
 * whatever splitting strategy is configured to run next — unlike plain text or a PDF, an Office
 * document's raw bytes aren't a format any later stage knows how to read directly.
 *
 * <p>Only usable as a pipeline's first stage: it needs the document's original bytes, which no
 * later stage — working from already-decoded {@link KnowledgeChunk} text — has access to.
 */
public final class OfficeTextExtractionStage extends ChunkingStage
    implements StreamingChunkingStage {

  @Override
  public Flowable<KnowledgeChunk> apply(final InputStream content) {
    final String text = new ApachePoiDocumentParser().parse(content).text();
    final KnowledgeChunk chunk = new KnowledgeChunk();
    chunk.setText(text);
    return Flowable.just(chunk);
  }

  @Override
  protected Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    final ApachePoiDocumentParser parser = new ApachePoiDocumentParser();
    return chunks.concatMap(
        chunk -> {
          if (!ChunkUtils.isText(chunk)) {
            return Flowable.just(chunk);
          }
          chunk.setText(
              parser
                  .parse(new ByteArrayInputStream(chunk.getText().getBytes(StandardCharsets.UTF_8)))
                  .text());
          return Flowable.just(chunk);
        });
  }
}

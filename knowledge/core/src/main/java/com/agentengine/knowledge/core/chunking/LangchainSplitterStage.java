package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.util.agents.beans.config.ChunkingType;
import com.agentengine.util.common.StringUtils;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Wraps LangChain4j's built-in splitters for {@code RECURSIVE}, {@code SENTENCE}, and {@code
 * PARAGRAPH} chunking types. Each input chunk is split by its text and the results are new chunks
 * stamped with {@code knowledge}'s id and agent id.
 */
public final class LangchainSplitterStage extends ChunkingStage {

  private final Knowledge knowledge;
  private final ChunkingType type;
  private final int maxSegmentSize;
  private final int maxOverlapSize;

  public LangchainSplitterStage(
      final Knowledge knowledge,
      final ChunkingType type,
      final int maxSegmentSize,
      final int maxOverlapSize) {
    this.knowledge = knowledge;
    this.type = type;
    this.maxSegmentSize = maxSegmentSize;
    this.maxOverlapSize = maxOverlapSize;
  }

  @Override
  protected boolean cpuBound() {
    return true;
  }

  @Override
  public Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    final DocumentSplitter splitter =
        switch (type) {
          case SENTENCE -> new DocumentBySentenceSplitter(maxSegmentSize, maxOverlapSize);
          case PARAGRAPH -> new DocumentByParagraphSplitter(maxSegmentSize, maxOverlapSize);
          default -> DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize);
        };
    // The chunk this stage receives when it runs first in a pipeline carries no identity — the
    // document's raw text is all ChunkingPipeline knows about it — so every chunk this stage
    // produces is stamped with its own knowledge rather than trusting whatever (if anything) the
    // parent already had.
    return ChunkUtils.splitChunks(
            chunks,
            text ->
                splitter.split(Document.from(text)).stream()
                    .map(TextSegment::text)
                    .filter(StringUtils::isNotBlank)
                    .toList())
        .doOnNext(chunk -> ChunkUtils.addMetadata(knowledge, chunk));
  }
}

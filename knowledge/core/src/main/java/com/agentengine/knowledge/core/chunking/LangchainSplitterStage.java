package com.agentengine.knowledge.core.chunking;

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
import java.util.List;

/**
 * Wraps LangChain4j's built-in splitters for {@code RECURSIVE}, {@code SENTENCE}, and
 * {@code PARAGRAPH} chunking types. Each input chunk is split by its text and the results
 * are new chunks inheriting the parent's metadata.
 */
public final class LangchainSplitterStage extends ChunkingStage {

    private final ChunkingType type;
    private final int maxSegmentSize;
    private final int maxOverlapSize;

    public LangchainSplitterStage(final ChunkingType type, final int maxSegmentSize, final int maxOverlapSize) {
        this.type = type;
        this.maxSegmentSize = maxSegmentSize;
        this.maxOverlapSize = maxOverlapSize;
    }

    @Override
    public List<KnowledgeChunk> apply(final List<KnowledgeChunk> chunks) {
        final DocumentSplitter splitter =
                switch (type) {
                    case SENTENCE -> new DocumentBySentenceSplitter(maxSegmentSize, maxOverlapSize);
                    case PARAGRAPH -> new DocumentByParagraphSplitter(maxSegmentSize, maxOverlapSize);
                    default -> DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize);
                };
        return ChunkUtils.splitChunks(
                chunks,
                text -> splitter.split(Document.from(text)).stream()
                        .map(TextSegment::text)
                        .filter(StringUtils::isNotBlank)
                        .toList());
    }
}

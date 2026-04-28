package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Utility methods for transforming {@link KnowledgeChunk} lists in chunking stages.
 */
public final class ChunkUtils {

    private ChunkUtils() {}

    /**
     * Splits each input chunk by applying {@code splitter} to its text, producing new chunks
     * that inherit the parent's {@code knowledgeId} and {@code agentId}. Chunk indices are
     * reassigned sequentially across all output chunks. Offsets are located by searching the
     * parent chunk's text.
     */
    public static List<KnowledgeChunk> splitChunks(
            final List<KnowledgeChunk> chunks, final Function<String, List<String>> splitter) {
        final List<KnowledgeChunk> result = new ArrayList<>();
        int globalIndex = 0;
        for (final KnowledgeChunk parent : chunks) {
            final String parentText = parent.getText() != null ? parent.getText() : "";
            final List<String> parts = splitter.apply(parentText);
            int searchFrom = 0;
            for (final String part : parts) {
                final KnowledgeChunk child = new KnowledgeChunk();
                child.setKnowledgeId(parent.getKnowledgeId());
                child.setAgentId(parent.getAgentId());
                child.setChunkIndex(globalIndex++);
                child.setText(part);
                // Locate offsets within the parent text
                final int start = parentText.indexOf(part, Math.max(0, searchFrom - part.length()));
                if (start >= 0) {
                    child.setChunkStart(parent.getChunkStart() + start);
                    child.setChunkEnd(parent.getChunkStart() + start + part.length());
                    searchFrom = start + part.length();
                } else {
                    child.setChunkStart(parent.getChunkStart());
                    child.setChunkEnd(parent.getChunkStart() + part.length());
                }
                result.add(child);
            }
        }
        return result;
    }

    /**
     * Merges a list of text strings into a single chunk, inheriting metadata from the first chunk.
     */
    public static KnowledgeChunk mergeTexts(final List<KnowledgeChunk> sources, final int index) {
        final KnowledgeChunk merged = new KnowledgeChunk();
        if (!sources.isEmpty()) {
            merged.setKnowledgeId(sources.getFirst().getKnowledgeId());
            merged.setAgentId(sources.getFirst().getAgentId());
            merged.setChunkStart(sources.getFirst().getChunkStart());
            merged.setChunkEnd(sources.getLast().getChunkEnd());
        }
        merged.setChunkIndex(index);

        final String mergedText = sources.stream()
                .map(chunk -> StringUtils.isNotBlank(chunk.getText()) ? chunk.getText() : "")
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        merged.setText(mergedText);
        return merged;
    }
}

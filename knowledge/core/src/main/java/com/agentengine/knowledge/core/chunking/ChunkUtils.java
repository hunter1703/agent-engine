package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Utility methods for transforming {@link KnowledgeChunk} lists in chunking stages. */
public final class ChunkUtils {

  /**
   * Length of the trailing anchor searched for when a part's own text isn't found verbatim (see
   * {@link #locateStart}). Long enough to be distinctive in natural-language text, short enough to
   * keep every search cheap.
   */
  private static final int OFFSET_ANCHOR_LENGTH = 64;

  /**
   * How far past {@code from} {@link #locateStart} will look for the anchor. A splitter can rejoin
   * adjacent units with a delimiter that doesn't match the source's own formatting, so a search
   * with no upper bound can land on a coincidental match far away in repetitive text (stage
   * directions, boilerplate) instead of failing — and since the search position only ever moves
   * forward, one such false match drags every later chunk's offset with it. Bounding the window
   * rules that out, and keeps every search cheap regardless of document size.
   */
  private static final int OFFSET_SEARCH_WINDOW = 8192;

  private ChunkUtils() {}

  /**
   * Splits each input chunk by applying {@code splitter} to its text, producing new chunks that
   * inherit the parent's {@code knowledgeId} and {@code agentId}. Chunk indices are reassigned
   * sequentially across all output chunks. Offsets are located by searching the parent chunk's
   * text, always moving forward from the previous part's end so the search stays anchored near
   * where the next part actually is instead of ever rescanning from further back.
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
        final int start = locateStart(parentText, part, searchFrom);
        child.setChunkStart(parent.getChunkStart() + start);
        child.setChunkEnd(parent.getChunkStart() + start + part.length());
        searchFrom = start + part.length();
        result.add(child);
      }
    }
    return result;
  }

  /**
   * Finds where {@code part} begins in {@code text} at or after {@code from}, by locating just its
   * trailing {@link #OFFSET_ANCHOR_LENGTH} characters within the next {@link #OFFSET_SEARCH_WINDOW}
   * characters and backing the start position out from there — equivalent to locating {@code part}
   * itself whenever it's no longer than the anchor. A splitter can stitch overlap text from the
   * previous part onto this one's front, making {@code part} as a whole not literally present in
   * {@code text}; searching only its tail sidesteps that, since the tail is always the genuinely
   * new content, unaffected by stitching. Never returns less than {@code from}, so a caller
   * chaining these calls across a whole document always searches a shrinking remainder instead of
   * rescanning the same span repeatedly. Falls back to {@code from} itself, clamped to {@code
   * text}'s length, if the anchor can't be located within the window — {@code from} is only ever an
   * estimate once a search has missed, and estimates can run past the true end near the last part
   * or two.
   */
  private static int locateStart(final String text, final String part, final int from) {
    final int clampedFrom = Math.min(from, text.length());
    final int anchorLength = Math.min(part.length(), OFFSET_ANCHOR_LENGTH);
    final String anchor = part.substring(part.length() - anchorLength);
    final int windowEnd = Math.min(text.length(), clampedFrom + OFFSET_SEARCH_WINDOW);
    final int foundAt = text.substring(clampedFrom, windowEnd).indexOf(anchor);
    if (foundAt < 0) {
      return clampedFrom;
    }
    return Math.max(clampedFrom, clampedFrom + foundAt - (part.length() - anchorLength));
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

    final String mergedText =
        sources.stream()
            .map(chunk -> StringUtils.isNotBlank(chunk.getText()) ? chunk.getText() : "")
            .reduce((a, b) -> a + "\n\n" + b)
            .orElse("");
    merged.setText(mergedText);
    return merged;
  }
}

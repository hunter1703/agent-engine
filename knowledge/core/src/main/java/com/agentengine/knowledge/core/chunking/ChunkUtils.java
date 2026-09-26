package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.FileUtils;
import com.agentengine.util.common.StringUtils;
import io.reactivex.rxjava3.core.Flowable;
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
   * Floor for how far past {@code from} {@link #locateStart} will look for the anchor. A splitter
   * can rejoin adjacent units with a delimiter that doesn't match the source's own formatting, so a
   * search with no upper bound can land on a coincidental match far away in repetitive text (stage
   * directions, boilerplate) instead of failing — and since the search position only ever moves
   * forward, one such false match drags every later chunk's offset with it. Bounding the window
   * rules that out, and keeps every search cheap regardless of document size. Only a floor, not the
   * actual window: the anchor sits near the end of a part, which can itself be longer than this
   * constant, so {@link #locateStart} widens the window to fit whenever {@code part} calls for it.
   */
  private static final int OFFSET_SEARCH_WINDOW = 8192;

  private ChunkUtils() {}

  /**
   * Stamps {@code chunk} with {@code knowledge}'s id and agent id — the identity every chunk
   * produced for one document shares, for a stage building chunks that have no parent of their own
   * to inherit it from.
   */
  public static void addMetadata(final Knowledge knowledge, final KnowledgeChunk chunk) {
    chunk.setKnowledgeId(knowledge.getId());
    chunk.setAgentId(knowledge.getAgentId());
  }

  /**
   * Whether {@code chunk} is sourced from plain text, per its {@link KnowledgeChunk#getMimeType()}
   * — {@code true} for every chunk except one sourced from some other media (see {@link #isMedia}).
   * {@code mimeType} names what the chunk is *about*, not the literal type of its {@code text}
   * field (always a plain string): a chunk describing an image keeps an image mime type
   * permanently, even once it holds a text description rather than raw bytes.
   */
  public static boolean isText(final KnowledgeChunk chunk) {
    return chunk.getMimeType() == null || chunk.getMimeType().startsWith("text/");
  }

  /**
   * Whether {@code chunk} is sourced from some non-text media (e.g. an image) rather than plain
   * text — set by a source stage that extracts embedded media (e.g. {@code PdfSplitterStage}) and
   * never cleared, including once {@code ImageChunkingStage} replaces its raw bytes with a text
   * description (see {@link KnowledgeChunk#getBytes()} for the separate "still needs describing"
   * signal). A splitting or merging stage checks this to leave such a chunk whole rather than
   * treating its text as content to divide or fold in, whether or not it's been described yet.
   */
  public static boolean isMedia(final KnowledgeChunk chunk) {
    return !isText(chunk);
  }

  /** Whether {@code chunk}'s mime type is one of the raster image formats. */
  public static boolean isImage(final KnowledgeChunk chunk) {
    return FileUtils.IMAGE_MIME_TYPES.contains(chunk.getMimeType());
  }

  /** {@code chunk}'s text length, or 0 if it has none. */
  public static int textLength(final KnowledgeChunk chunk) {
    return chunk.getText() != null ? chunk.getText().length() : 0;
  }

  /**
   * Splits each chunk in {@code parents} by applying {@code splitter} to its text, producing new
   * chunks that inherit the parent's {@code knowledgeId} and {@code agentId}. {@code chunkIndex} is
   * left unset — whatever ultimately consumes the full chunk stream assigns the real, sequential
   * index once it knows the final ordering, so there's nothing for an intermediate stage to
   * usefully assign here. Offsets are located by searching the parent chunk's text, always moving
   * forward from the previous part's end so the search stays anchored near where the next part
   * actually is instead of ever rescanning from further back.
   *
   * <p>Each parent's own split still needs that parent's whole text before it can decide any
   * boundary within it — that doesn't change here. But {@code parents} itself is consumed one
   * element at a time: this parent's children are emitted downstream as soon as they're ready,
   * without waiting on any parent after it, so a caller upstream of many parents (e.g. many
   * paragraphs each split further) isn't forced to buffer all of them before this stage can start
   * producing anything.
   */
  public static Flowable<KnowledgeChunk> splitChunks(
      final Flowable<KnowledgeChunk> parents, final Function<String, List<String>> splitter) {
    return parents.concatMap(parent -> Flowable.fromIterable(split(parent, splitter)));
  }

  private static List<KnowledgeChunk> split(
      final KnowledgeChunk parent, final Function<String, List<String>> splitter) {
    if (isMedia(parent)) {
      return List.of(parent);
    }
    final String parentText = parent.getText() != null ? parent.getText() : "";
    final List<String> parts = splitter.apply(parentText);
    final List<KnowledgeChunk> result = new ArrayList<>(parts.size());
    int searchFrom = 0;
    for (final String part : parts) {
      final KnowledgeChunk child = new KnowledgeChunk();
      child.setKnowledgeId(parent.getKnowledgeId());
      child.setAgentId(parent.getAgentId());
      child.setText(part);
      final int start = locateStart(parentText, part, searchFrom);
      child.setChunkStart(parent.getChunkStart() + start);
      child.setChunkEnd(parent.getChunkStart() + start + part.length());
      searchFrom = start + part.length();
      result.add(child);
    }
    return result;
  }

  /**
   * Finds where {@code part} begins in {@code text} at or after {@code from}, by locating just its
   * trailing {@link #OFFSET_ANCHOR_LENGTH} characters within the next {@code max(part.length(),
   * OFFSET_SEARCH_WINDOW)} characters and backing the start position out from there — equivalent to
   * locating {@code part} itself whenever it's no longer than the anchor. A splitter can stitch
   * overlap text from the previous part onto this one's front, making {@code part} as a whole not
   * literally present in {@code text}; searching only its tail sidesteps that, since the tail is
   * always the genuinely new content, unaffected by stitching. Never returns less than {@code
   * from}, so a caller chaining these calls across a whole document always searches a shrinking
   * remainder instead of rescanning the same span repeatedly. Falls back to {@code from} itself,
   * clamped to {@code text}'s length, if the anchor can't be located within the window — {@code
   * from} is only ever an estimate once a search has missed, and estimates can run past the true
   * end near the last part or two.
   *
   * <p>The window always grows to cover at least 1.5x {@code part.length()}: the anchor sits near
   * {@code part}'s own end, so a window only as wide as {@code part} itself would leave no slack
   * for the true position to have drifted past where {@code part} alone would predict (overlap and
   * rejoin-delimiter differences do exactly that) — the search would then hunt right up to the
   * anchor's expected position and stop just short of it. The margin costs nothing when {@code
   * part} is short (the {@link #OFFSET_SEARCH_WINDOW} floor already covers it); it only matters
   * once a caller configures {@code maxSegmentSize} large enough for that floor not to apply.
   */
  private static int locateStart(final String text, final String part, final int from) {
    final int clampedFrom = Math.min(from, text.length());
    final int anchorLength = Math.min(part.length(), OFFSET_ANCHOR_LENGTH);
    final String anchor = part.substring(part.length() - anchorLength);
    final int window = Math.max(OFFSET_SEARCH_WINDOW, (int) (part.length() * 1.5));
    final int windowEnd = Math.min(text.length(), clampedFrom + window);
    final int foundAt = text.substring(clampedFrom, windowEnd).indexOf(anchor);
    if (foundAt < 0) {
      return clampedFrom;
    }
    return Math.max(clampedFrom, clampedFrom + foundAt - (part.length() - anchorLength));
  }

  /**
   * Merges a list of text strings into a single chunk, inheriting metadata from the first chunk.
   * {@code chunkIndex} is left unset, same as {@link #split} — a merge stage doesn't know the final
   * sequential ordering either.
   */
  public static KnowledgeChunk mergeTexts(final List<KnowledgeChunk> sources) {
    final KnowledgeChunk merged = new KnowledgeChunk();
    if (!sources.isEmpty()) {
      merged.setKnowledgeId(sources.getFirst().getKnowledgeId());
      merged.setAgentId(sources.getFirst().getAgentId());
      merged.setChunkStart(sources.getFirst().getChunkStart());
      merged.setChunkEnd(sources.getLast().getChunkEnd());
    }

    final String mergedText =
        sources.stream()
            .map(chunk -> StringUtils.isNotBlank(chunk.getText()) ? chunk.getText() : "")
            .reduce((a, b) -> a + "\n\n" + b)
            .orElse("");
    merged.setText(mergedText);
    return merged;
  }
}

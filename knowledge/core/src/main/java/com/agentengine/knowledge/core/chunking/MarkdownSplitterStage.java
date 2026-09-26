package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits Markdown text into sections at ATX headers ({@code #} through {@code ######}) — each
 * section starts at a header line and runs up to (not including) the next one, so a chunk always
 * carries the heading it belongs under.
 *
 * <p>Purely structural: section length is otherwise unbounded, and it makes no attempt at further
 * semantic refinement within a section. {@code MarkdownKnowledgeIndexer} runs this as the
 * pipeline's first stage, with the knowledge's own configured chunking strategy (e.g. {@code
 * SEMANTIC} or {@code SENTENCE}) running next to refine and bound each section.
 */
public final class MarkdownSplitterStage extends ChunkingStage {

  private static final Pattern ATX_HEADER = Pattern.compile("^#{1,6}[ \\t].*");

  @Override
  protected boolean cpuBound() {
    return true;
  }

  @Override
  protected Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return ChunkUtils.splitChunks(chunks, MarkdownSplitterStage::splitSections);
  }

  private static List<String> splitSections(final String text) {
    final List<String> sections = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    for (final String line : text.split("\n", -1)) {
      if (!current.isEmpty() && ATX_HEADER.matcher(line).matches()) {
        sections.add(current.toString());
        current.setLength(0);
      }
      if (!current.isEmpty()) {
        current.append('\n');
      }
      current.append(line);
    }
    if (!current.isEmpty()) {
      sections.add(current.toString());
    }
    return sections;
  }
}

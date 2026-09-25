package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
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
 * semantic refinement within a section. Configure it as the first entry of {@link
 * com.agentengine.util.agents.beans.config.KnowledgeSettings#getChunkingStrategy()}'s ordered list
 * with a second entry (e.g. {@code SEMANTIC} or {@code SENTENCE}) to refine and bound each section
 * — {@link ChunkingPipelineFactory} chains every configured strategy's stages into one pipeline, so
 * the second strategy's stages run on this one's output.
 */
public final class MarkdownSplitterStage extends ChunkingStage {

  private static final Pattern ATX_HEADER = Pattern.compile("^#{1,6}[ \\t].*");

  private final Knowledge knowledge;

  public MarkdownSplitterStage(final Knowledge knowledge) {
    this.knowledge = knowledge;
  }

  @Override
  protected boolean cpuBound() {
    return true;
  }

  @Override
  protected Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    // The chunk this stage receives when it runs first in a pipeline carries no identity — see
    // LangchainSplitterStage for why every chunk produced is stamped from this stage's own
    // knowledge rather than trusting whatever (if anything) the parent already had.
    return ChunkUtils.splitChunks(chunks, MarkdownSplitterStage::splitSections)
        .doOnNext(chunk -> ChunkUtils.addMetadata(knowledge, chunk));
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

package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.util.common.StringUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits each input chunk's text into fixed-size character windows with a fixed character overlap —
 * no boundary detection, no language model, no post-hoc search for where a chunk landed in the
 * source text. Offsets are exact by construction, since each window is a direct substring slice of
 * the parent's own text at a known position. The cheapest available chunking strategy, scaling
 * predictably with document size regardless of its structure or language.
 */
public final class FixedWindowSplitterStage extends ChunkingStage {

  private final int maxSegmentSize;
  private final int maxOverlapSize;

  public FixedWindowSplitterStage(final int maxSegmentSize, final int maxOverlapSize) {
    this.maxSegmentSize = maxSegmentSize;
    this.maxOverlapSize = maxOverlapSize;
  }

  @Override
  protected boolean cpuBound() {
    return true;
  }

  @Override
  public List<KnowledgeChunk> apply(final List<KnowledgeChunk> chunks) {
    final List<KnowledgeChunk> result = new ArrayList<>();
    final int step = Math.max(1, maxSegmentSize - maxOverlapSize);
    int globalIndex = 0;
    for (final KnowledgeChunk parent : chunks) {
      final String text = parent.getText() != null ? parent.getText() : "";
      for (int start = 0; start < text.length(); start += step) {
        final int end = Math.min(start + maxSegmentSize, text.length());
        final String part = text.substring(start, end);
        if (StringUtils.isNotBlank(part)) {
          final KnowledgeChunk child = new KnowledgeChunk();
          child.setKnowledgeId(parent.getKnowledgeId());
          child.setAgentId(parent.getAgentId());
          child.setChunkIndex(globalIndex++);
          child.setText(part);
          child.setChunkStart(parent.getChunkStart() + start);
          child.setChunkEnd(parent.getChunkStart() + end);
          result.add(child);
        }
        if (end == text.length()) {
          break;
        }
      }
    }
    return result;
  }
}

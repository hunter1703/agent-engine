package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.utils.StringUtils;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Splits the document into fixed-size character windows with a fixed character overlap — no
 * boundary detection, no language model, no post-hoc search for where a chunk landed in the source
 * text.
 */
public final class FixedWindowChunkingStage extends IndexingStage {

  private final int maxSegmentSize;
  private final int maxOverlapSize;

  public FixedWindowChunkingStage(final int maxSegmentSize, final int maxOverlapSize) {
    this.maxSegmentSize = maxSegmentSize;
    this.maxOverlapSize = maxOverlapSize;
  }

  @Override
  protected boolean cpuBound() {
    return true;
  }

  @Override
  public Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return chunks.concatMap(this::split);
  }

  private Flowable<KnowledgeChunk> split(final KnowledgeChunk parent) {
    if (ChunkUtils.isMedia(parent)) {
      return Flowable.just(parent);
    }
    final String text = parent.getText() != null ? parent.getText() : "";
    final int step = Math.max(1, maxSegmentSize - maxOverlapSize);
    return Flowable.generate(
        () -> new int[] {0},
        (state, emitter) -> {
          int start = state[0];
          while (start < text.length()) {
            final int end = Math.min(start + maxSegmentSize, text.length());
            final boolean atEnd = end == text.length();
            final String part = text.substring(start, end);
            if (StringUtils.isNotBlank(part)) {
              final KnowledgeChunk child = new KnowledgeChunk();
              child.setText(part);
              child.setChunkStart(parent.getChunkStart() + start);
              child.setChunkEnd(parent.getChunkStart() + end);
              emitter.onNext(child);
              state[0] = atEnd ? text.length() : start + step;
              return;
            }
            if (atEnd) {
              break;
            }
            start += step;
          }
          state[0] = text.length();
          emitter.onComplete();
        });
  }
}

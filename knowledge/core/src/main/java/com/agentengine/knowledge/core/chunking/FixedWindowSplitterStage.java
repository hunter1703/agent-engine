package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.knowledge.api.chunking.StreamingChunkingStage;
import com.agentengine.util.common.StringUtils;
import io.reactivex.rxjava3.core.Flowable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

/**
 * Splits the document into fixed-size character windows with a fixed character overlap — no
 * boundary detection, no language model, no post-hoc search for where a chunk landed in the source
 * text. Offsets are exact by construction, since each window is read directly off the source at a
 * known position. The cheapest available chunking strategy, scaling predictably with document size
 * regardless of its structure or language.
 *
 * <p>When this is the pipeline's first stage, {@link #apply(Reader)} reads the source incrementally
 * — one window's worth of characters at a time, sliding forward by {@code maxSegmentSize -
 * maxOverlapSize} — since arithmetic windowing needs no semantic understanding of the text and so,
 * unlike a strategy that must see whole sentences or paragraphs, never has reason to hold more than
 * the current window in memory regardless of document size. {@link #apply(Flowable)} is used
 * instead when this stage runs later in a configured chain, splitting each already-produced chunk's
 * text the same way.
 */
public final class FixedWindowSplitterStage extends ChunkingStage
    implements StreamingChunkingStage {

  private final Knowledge knowledge;
  private final int maxSegmentSize;
  private final int maxOverlapSize;

  public FixedWindowSplitterStage(
      final Knowledge knowledge, final int maxSegmentSize, final int maxOverlapSize) {
    this.knowledge = knowledge;
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

  /**
   * Reads one window at a time via {@link BufferedReader#mark}/{@link BufferedReader#reset}: mark
   * the window's start, read a full window's worth of characters, then reset back to the mark and
   * skip forward by {@code step} to land on the next window's start — the overlap is simply
   * whatever lies between the new position and where the just-read window ended. The buffer is
   * sized to {@code maxSegmentSize} up front so the first {@code mark} call never has to grow it.
   */
  @Override
  public Flowable<KnowledgeChunk> apply(final Reader content) {
    final BufferedReader reader = new BufferedReader(content, maxSegmentSize);
    final int step = Math.max(1, maxSegmentSize - maxOverlapSize);
    final char[] buffer = new char[maxSegmentSize];
    return Flowable.generate(
        () -> new int[] {0}, // [0] = absolute offset of the window about to be read
        (offset, emitter) -> {
          try {
            reader.mark(maxSegmentSize);
            final int read = readFully(reader, buffer);
            if (read == 0) {
              emitter.onComplete();
              return;
            }
            emitter.onNext(toChunk(buffer, read, offset[0]));
            if (read < buffer.length) {
              emitter.onComplete();
              return;
            }
            reader.reset();
            offset[0] += reader.skip(step);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  private KnowledgeChunk toChunk(final char[] buffer, final int length, final int offset) {
    final KnowledgeChunk child = new KnowledgeChunk();
    ChunkUtils.addMetadata(knowledge, child);
    child.setText(new String(buffer, 0, length));
    child.setChunkStart(offset);
    child.setChunkEnd(offset + length);
    return child;
  }

  /** Reads until {@code buffer} is full or the source is exhausted, whichever comes first. */
  private static int readFully(final Reader content, final char[] buffer) throws IOException {
    int total = 0;
    while (total < buffer.length) {
      final int read = content.read(buffer, total, buffer.length - total);
      if (read < 0) {
        break;
      }
      total += read;
    }
    return total;
  }

  private Flowable<KnowledgeChunk> split(final KnowledgeChunk parent) {
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
              ChunkUtils.addMetadata(knowledge, child);
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

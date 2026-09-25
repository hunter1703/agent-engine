package com.agentengine.knowledge.api.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import io.reactivex.rxjava3.core.Flowable;

/**
 * A single step in a {@link ChunkingPipeline}.
 *
 * <p>Each stage transforms a stream of {@link KnowledgeChunk}s from the previous stage into a
 * refined stream. The first stage always receives a single-element stream containing a chunk with
 * only the full document text set (no vector, no offsets yet).
 *
 * <p>Splitting stages produce new chunks from the text of each input chunk. A stage that needs to
 * see several/all upstream chunks at once to do its work (e.g. to compare adjacent chunks, or
 * window several together) buffers what it needs internally — the pipeline itself never forces that
 * buffering. The embedding stage populates the vector on each chunk.
 *
 * <p>A fresh set of stages is built for every indexing run (see {@code ChunkingPipelineFactory}) —
 * never reused across documents or shared as a singleton — so an implementation is free to hold
 * state specific to the document it was built for (e.g. its {@code knowledgeId}).
 */
public abstract class ChunkingStage {

  protected abstract Flowable<KnowledgeChunk> apply(Flowable<KnowledgeChunk> chunks);

  /**
   * Whether this stage is pure computation with no blocking I/O. The pipeline runs such stages on a
   * dedicated platform thread pool, since a virtual thread never yields its carrier while
   * computing.
   */
  protected boolean cpuBound() {
    return false;
  }
}
